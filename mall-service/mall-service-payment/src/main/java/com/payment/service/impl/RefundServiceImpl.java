package com.payment.service.impl;

import com.model.event.RefundRequestEvent;
import com.model.exception.BusinessException;
import com.payment.channel.PayChannel;
import com.payment.channel.RefundResult;
import com.payment.config.PaymentSdkProperties;
import com.payment.entity.PayOrder;
import com.payment.entity.Refund;
import com.payment.mapper.RefundMapper;
import com.payment.mapper.PayOrderMapper;
import com.payment.service.RefundService;
import com.payment.service.RefundSettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 退款执行实现。
 *
 * 失败策略：渠道明确拒绝时**抛异常**而不是把退款单置失败。原因是订单此刻还停在
 * 5退款中，置失败会造成「订单说退款中、退款单说失败」的永久不一致，且没有任何机制
 * 会去修正它。抛出后由统一的有界重试（3 次）兜住瞬时故障，耗尽后落 q.pay.dlq 等人工介入，
 * 语义上退款单仍是「退款中」——钱确实还没退出去，这是诚实的描述。
 *
 * 重试安全性由 refund_no 保证：它同时是渠道的 out_request_no / out_refund_no，
 * 渠道按它幂等，重复提交不会重复出款。
 */
@Service
@Slf4j
public class RefundServiceImpl implements RefundService {

    @Autowired
    RefundMapper refundMapper;
    @Autowired
    PayOrderMapper payOrderMapper;
    @Autowired
    RefundSettleService refundSettleService;
    @Autowired
    List<PayChannel> channels;
    @Autowired
    PaymentSdkProperties properties;

    @Override
    public void handleRequest(RefundRequestEvent event) {
        if (event == null || event.getAction() == null) {
            log.warn("[pay] 收到无 action 的退款指令，忽略");
            return;
        }
        switch (event.getAction()) {
            case RefundRequestEvent.ACTION_APPLY -> createRefund(event);
            case RefundRequestEvent.ACTION_APPROVE -> executeRefund(event);
            case RefundRequestEvent.ACTION_REJECT -> rejectRefund(event);
            default -> log.warn("[pay] 未知退款指令 action={} refundNo={}", event.getAction(), event.getRefundNo());
        }
    }

    /** APPLY：只建单不动钱（等审核）。同 refundNo 重复投递直接跳过。 */
    private void createRefund(RefundRequestEvent event) {
        if (refundMapper.selectByRefundNo(event.getRefundNo()) != null) {
            log.info("[pay] 退款单 {} 已存在，跳过重复建单", event.getRefundNo());
            return;
        }
        PayOrder paid = payOrderMapper.selectPaidByOrderId(event.getOrderId());
        if (paid == null) {
            // 可申请退款的订单必然已支付成功；查不到说明支付侧数据异常，抛出让重试并最终落 DLQ
            throw new IllegalStateException("[pay] 订单 " + event.getOrderId() + " 无支付成功的支付单，无法建退款单");
        }
        Refund refund = new Refund();
        refund.setRefundNo(event.getRefundNo());
        refund.setOrderId(event.getOrderId());
        refund.setPayOrderId(paid.getId());
        refund.setUserId(event.getUserId());
        refund.setRefundAmount(event.getRefundAmount());
        refund.setRefundReason(event.getRefundReason());
        refundMapper.insertRefund(refund);
        log.info("[pay] 已建退款单 refundNo={} orderId={} amount={}（待审核，未动钱）",
                event.getRefundNo(), event.getOrderId(), event.getRefundAmount());
    }

    /** APPROVE：调渠道原路退回，成功后事务落库并回发 pay.refund.success */
    private void executeRefund(RefundRequestEvent event) {
        Refund refund = refundMapper.selectByRefundNo(event.getRefundNo());
        if (refund == null) {
            throw new IllegalStateException("[pay] 退款单不存在 refundNo=" + event.getRefundNo());
        }
        if (refund.getRefundStatus() == null || refund.getRefundStatus() != RefundMapper.STATUS_REFUNDING) {
            log.info("[pay] 退款单 {} 已终结（status={}），跳过重复打款", event.getRefundNo(), refund.getRefundStatus());
            return;
        }
        PayOrder paid = payOrderMapper.selectPaidByOrderId(refund.getOrderId());
        if (paid == null) {
            throw new IllegalStateException("[pay] 订单 " + refund.getOrderId() + " 无支付成功支付单，无法退款");
        }

        RefundResult result;
        if (properties.getMock() != null && properties.getMock().isEnabled()) {
            // 演示钩子：与 /pay/mock/success 同一开关，占位渠道下也能跑通整条退款链路
            result = RefundResult.ok("MOCKREFUND" + System.currentTimeMillis());
            log.info("[pay] 模拟退款（payment.mock.enabled=true）refundNo={}", refund.getRefundNo());
        } else {
            PayChannel channel = findChannel(paid.getPaymentMethod());
            // 渠道调用在事务外（网络 IO 不进事务）
            result = channel.refund(paid, refund.getRefundNo(), refund.getRefundAmount(), refund.getRefundReason());
        }

        if (!result.isSuccess()) {
            throw new IllegalStateException("[pay] 退款被渠道拒绝 refundNo=" + refund.getRefundNo()
                    + "：" + result.getMessage());
        }
        refundSettleService.settleSuccess(refund, paid, result.getRefundTransactionId());
    }

    /** REJECT：审核驳回，钱从未动过，把退款单置失败收尾 */
    private void rejectRefund(RefundRequestEvent event) {
        int affected = refundMapper.markRefundFailed(event.getRefundNo());
        if (affected == 0) {
            log.info("[pay] 退款单 {} 非退款中或不存在，跳过驳回处理", event.getRefundNo());
            return;
        }
        log.info("[pay] 退款单 {} 因审核驳回置为失败：{}", event.getRefundNo(), event.getRejectReason());
    }

    private PayChannel findChannel(Integer method) {
        if (method == null) {
            throw new BusinessException("支付单缺少支付方式，无法退款");
        }
        for (PayChannel channel : channels) {
            if (channel.method() == method) {
                return channel;
            }
        }
        throw new BusinessException("不支持的支付方式：" + method);
    }
}
