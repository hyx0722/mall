package com.model.event;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款到账事件（payment 服务 -> RabbitMQ -> order 服务）。
 *
 * payment 把 refund 单置 1-退款成功、pay_order 置 2-已退款后，与业务落库同事务写入 outbox，
 * order 侧消费后把订单 5退款中 -> 6已退款（条件更新，天然防重复回调），
 * 并在同一事务内登记 order.refunded 事件供 inventory 回补库存。
 */
@Data
public class PayRefundSuccessEvent {

    /** 退款单号 */
    private String refundNo;

    /** 业务订单 id（order 侧 markRefunded 关联键） */
    private Long orderId;

    /** 买家用户 id */
    private Long userId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 渠道退款流水号（支付宝退款单号 / 微信退款单号） */
    private String refundTransactionId;
}
