package com.payment.channel;

import lombok.Data;

/**
 * 渠道退款结果。
 * 与下单不同，退款没有「返回给前端渲染」的形态，只有「钱出去了没有」，
 * 故这里用最简单的成功/失败 + 渠道退款单号表达。
 */
@Data
public class RefundResult {

    /** 渠道是否受理并完成退款（微信 PROCESSING 视为已受理，见 WxChannel.refund 说明） */
    private boolean success;

    /** 渠道退款单号（支付宝 trade_no / 微信 refund_id），失败时为空 */
    private String refundTransactionId;

    /** 失败原因或渠道提示 */
    private String message;

    public static RefundResult ok(String refundTransactionId) {
        RefundResult r = new RefundResult();
        r.setSuccess(true);
        r.setRefundTransactionId(refundTransactionId);
        r.setMessage("退款成功");
        return r;
    }

    public static RefundResult fail(String message) {
        RefundResult r = new RefundResult();
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }
}
