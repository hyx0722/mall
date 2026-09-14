package com.payment.service;

import com.model.event.RefundRequestEvent;

/**
 * 退款执行（payment 侧）。
 *
 * payment 不掌握退款状态机（那在 order 的 order_refund），只按 order 发来的指令办事：
 *   APPLY   -> 建退款单（refund_status=0 退款中），此时不动钱
 *   APPROVE -> 调渠道原路退回，成功后置退款单成功、支付单已退款，并回发 pay.refund.success
 *   REJECT  -> 退款单置失败（钱从未动过）
 */
public interface RefundService {

    /** MQ 入口：按 event.action 分派到建单 / 打款 / 关闭 */
    void handleRequest(RefundRequestEvent event);
}
