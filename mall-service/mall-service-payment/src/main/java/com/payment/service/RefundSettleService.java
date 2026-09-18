package com.payment.service;

import com.mall.common.outbox.OutboxService;
import com.model.event.PayRefundSuccessEvent;
import com.payment.config.PaymentRabbitConfig;
import com.payment.entity.PayOrder;
import com.payment.entity.Refund;
import com.payment.mapper.PayOrderMapper;
import com.payment.mapper.RefundMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款落库的事务漏斗（与 order 侧 OrderCancelService 同一角色）。
 *
 * 单独成 bean 的原因：打款必须先调渠道（网络 IO，绝不能包在事务里），
 * 渠道成功后才开事务落库。若把 settleSuccess 写在 RefundServiceImpl 内部私有方法上，
 * Spring 的自调用不走代理，@Transactional 会静默失效——那样「退款单已成功但
 * pay.refund.success 没入 outbox」就会丢事件，订单永远卡在退款中。
 *
 * 事务内三件事原子：退款单 0->1、支付单 1->2、pay.refund.success 入发件箱。
 */
@Service
@Slf4j
public class RefundSettleService {

    @Autowired
    RefundMapper refundMapper;
    @Autowired
    PayOrderMapper payOrderMapper;
    @Autowired
    OutboxService outboxService;

    @Transactional
    public void settleSuccess(Refund refund, PayOrder payOrder, String refundTransactionId) {
        // 条件更新 0->1：渠道重复回调/消息重投时拿到 0 行，直接返回不再发事件
        int affected = refundMapper.markRefundSuccess(refund.getRefundNo());
        if (affected == 0) {
            log.info("[pay] 退款单 {} 非退款中，跳过重复的退款成功处理", refund.getRefundNo());
            return;
        }
        payOrderMapper.markRefunded(payOrder.getId());

        PayRefundSuccessEvent event = new PayRefundSuccessEvent();
        event.setRefundNo(refund.getRefundNo());
        event.setOrderId(refund.getOrderId());
        event.setUserId(refund.getUserId());
        event.setRefundAmount(refund.getRefundAmount());
        event.setRefundTransactionId(refundTransactionId);
        outboxService.enqueue(PaymentRabbitConfig.ORDER_EXCHANGE,
                PaymentRabbitConfig.RK_PAY_REFUND_SUCCESS, null, event);
        log.info("[pay] 退款成功已落库并登记 pay.refund.success refundNo={} orderId={}",
                refund.getRefundNo(), refund.getOrderId());
    }
}
