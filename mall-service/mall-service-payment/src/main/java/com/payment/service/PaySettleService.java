package com.payment.service;

import com.mall.common.outbox.OutboxService;
import com.model.event.PaySuccessEvent;
import com.payment.config.PaymentRabbitConfig;
import com.payment.entity.PayOrder;
import com.payment.entity.PaymentRecord;
import com.payment.mapper.PayOrderMapper;
import com.payment.mapper.PaymentRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付落库的事务漏斗（与 RefundSettleService 同一角色，见其类注释）。
 *
 * 单独成 bean 的原因：支付成功有三条入口——支付宝回调、微信回调、模拟支付钩子。
 * 若把 settleSuccess 放在 PayOrderServiceImpl 内部，由同类方法直接调用，
 * Spring 的自调用不走代理，@Transactional 会**静默失效**——于是「支付单已置成功」
 * 与「pay.success 已入发件箱」不再原子，进程在两条语句之间崩溃就会丢事件，
 * 订单永远卡在待付款。抽成独立 bean 后所有入口都经由代理调用，事务边界不可绕过。
 *
 * 事务内三件事原子：支付单 0->1、回调记录落库、pay.success 入发件箱。
 */
@Service
@Slf4j
public class PaySettleService {

    @Autowired
    PayOrderMapper payOrderMapper;
    @Autowired
    PaymentRecordMapper paymentRecordMapper;
    @Autowired
    OutboxService outboxService;

    @Transactional
    public boolean settleSuccess(String payNo, String transactionId, String notifyType, String rawNotify) {
        PayOrder payOrder = payOrderMapper.selectByPayNo(payNo);
        if (payOrder == null) {
            log.warn("[pay] 支付单不存在，忽略回调 payNo={}", payNo);
            return false;
        }
        // 幂等：已支付成功（重复回调）直接视为成功，不再发事件
        if (payOrder.getPaymentStatus() != null && payOrder.getPaymentStatus() == 1) {
            return true;
        }
        // 条件更新 0->1（防并发重复推进），仅受影响的第一次真正落库并发事件
        int affected = payOrderMapper.markPaid(payNo, transactionId);
        if (affected == 0) {
            return true;
        }

        PaymentRecord record = new PaymentRecord();
        record.setPayOrderId(payOrder.getId());
        record.setPayNo(payNo);
        record.setTransactionId(transactionId);
        record.setNotifyType(notifyType);
        record.setNotifyContent(rawNotify);
        record.setHandleStatus(1);
        paymentRecordMapper.insertRecord(record);

        final PaySuccessEvent event = new PaySuccessEvent();
        event.setPayNo(payNo);
        event.setOrderId(payOrder.getOrderId());
        event.setUserId(payOrder.getUserId());
        event.setTransactionId(transactionId);
        event.setPaymentMethod(payOrder.getPaymentMethod());
        // 与支付落库同事务写 outbox，由 relay 提交后可靠投递（下游订单翻转只见已落库的支付成功）
        outboxService.enqueue(PaymentRabbitConfig.ORDER_EXCHANGE, PaymentRabbitConfig.RK_PAY_SUCCESS, null, event);
        log.info("[pay] 支付成功已落库并登记 pay.success payNo={} orderId={}", payNo, event.getOrderId());
        return true;
    }
}
