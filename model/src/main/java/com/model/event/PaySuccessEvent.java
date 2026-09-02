package com.model.event;

import lombok.Data;

/**
 * 支付成功事件（payment 服务 -> RabbitMQ -> order 服务）
 * 由 payment 在收到渠道支付成功（真实异步回调或测试钩子）落库后发布，
 * order 侧消费后把订单从「待付款」置为「待发货」。
 * 关联键使用 orderId（pay_order 仅存 order_id，且 settle 在无登录态线程执行，不做二次回查）。
 */
@Data
public class PaySuccessEvent {

    /** 支付单号（发给第三方的 out_trade_no） */
    private String payNo;

    /** 业务订单 id（order 侧 markPaid 关联键） */
    private Long orderId;

    /** 买家用户 id */
    private Long userId;

    /** 第三方支付流水号（支付宝交易号 / 微信支付单号） */
    private String transactionId;

    /** 支付方式：1-支付宝，2-微信 */
    private Integer paymentMethod;
}
