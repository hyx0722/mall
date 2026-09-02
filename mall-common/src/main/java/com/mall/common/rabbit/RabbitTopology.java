package com.mall.common.rabbit;

/**
 * 下单/扣库存/支付/取消事件流的 RabbitMQ 拓扑常量（纯字符串，无 amqp 依赖）。
 *
 * 原 order 与 inventory（及后加入的 payment）各自重复维护同一套 exchange/routingKey/queue 名，
 * 收敛于此避免两端漂移导致绑定错位。
 *
 * exchange: mall.order.exchange (topic, durable)
 *   order 发布 order.created  -> inventory 消费
 *   order 发布 order.canceled -> inventory 释放锁定 / payment 关闭未付支付单（支付超时自动取消）
 *   inventory 回执 inventory.deducted / inventory.deduct_failed -> order 消费
 *   payment 发布 pay.success -> order 消费（支付成功：待付款 -> 待发货）
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    public static final String ORDER_EXCHANGE = "mall.order.exchange";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_ORDER_CANCELED = "order.canceled";
    public static final String RK_DEDUCTED = "inventory.deducted";
    public static final String RK_DEDUCT_FAILED = "inventory.deduct_failed";
    public static final String RK_PAY_SUCCESS = "pay.success";

    /** 库存侧：消费下单事件 */
    public static final String Q_ORDER_CREATED = "q.inventory.order.created";
    /** 库存侧：消费订单取消事件（释放锁定） */
    public static final String Q_INVENTORY_ORDER_CANCELED = "q.inventory.order.canceled";
    /** 支付侧：消费订单取消事件（关闭未付支付单） */
    public static final String Q_PAY_ORDER_CANCELED = "q.pay.order.canceled";
    /** 订单侧：扣减成功/失败回执 */
    public static final String Q_DEDUCTED = "q.order.deducted";
    public static final String Q_DEDUCT_FAILED = "q.order.deduct.failed";
    /** 订单侧：支付成功回执 */
    public static final String Q_PAY_SUCCESS = "q.order.pay.success";
}
