package com.mall.common.rabbit;

/**
 * 下单/扣库存事件流的 RabbitMQ 拓扑常量（纯字符串，无 amqp 依赖）。
 *
 * 原 order 与 inventory 两侧各自重复维护同一套 exchange/routingKey/queue 名，
 * 收敛于此避免两端漂移导致绑定错位。
 *
 * exchange: mall.order.exchange (topic, durable)
 *   order 发布 order.created  -> inventory 消费
 *   inventory 回执 inventory.deducted / inventory.deduct_failed -> order 消费
 */
public final class RabbitTopology {

    private RabbitTopology() {
    }

    public static final String ORDER_EXCHANGE = "mall.order.exchange";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_DEDUCTED = "inventory.deducted";
    public static final String RK_DEDUCT_FAILED = "inventory.deduct_failed";

    /** 库存侧：消费下单事件 */
    public static final String Q_ORDER_CREATED = "q.inventory.order.created";
    /** 订单侧：扣减成功/失败回执 */
    public static final String Q_DEDUCTED = "q.order.deducted";
    public static final String Q_DEDUCT_FAILED = "q.order.deduct.failed";
}
