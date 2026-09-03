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
 *
 * 支付超时走延迟消息：mall.order.delay.exchange 收到带 per-message TTL 的超时标记，
 * 进入无消费者持有队列 q.delay.order.timeout，TTL 到点死信回主交换机 order.timeout -> q.order.timeout。
 *
 * 消费失败统一死信：各入站队列带 x-dead-letter-exchange=mall.order.dlx，经有界重试耗尽后
 * reject（requeue=false）落入各服务 DLQ。
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

    // ---------- 支付超时延迟消息（DLX + per-message TTL） ----------

    /** 延迟交换机：超时标记带 per-message TTL 发到这里，进入无消费者持有队列等 TTL */
    public static final String DELAY_EXCHANGE = "mall.order.delay.exchange";
    /** 持有队列入站路由键 */
    public static final String RK_DELAY_ORDER_TIMEOUT = "delay.order.timeout";
    /** 持有队列（无消费者；TTL 到点死信到主交换机） */
    public static final String Q_DELAY_ORDER_TIMEOUT = "q.delay.order.timeout";
    /** TTL 到点死信到主交换机时携带的路由键（需持队列 x-dead-letter-routing-key 一致） */
    public static final String RK_ORDER_TIMEOUT = "order.timeout";
    /** order 消费延迟超时标记的队列 */
    public static final String Q_ORDER_TIMEOUT = "q.order.timeout";

    // ---------- 死信（DLX / DLQ，各入站队列消费失败/重试耗尽后落死信） ----------

    /** 统一死信交换机（topic, durable；order/inventory/payment 各自声明同名） */
    public static final String DLX_EXCHANGE = "mall.order.dlx";
    /** 死信队列：各服务各自声明并绑定到 DLX / # */
    public static final String Q_ORDER_DLQ = "q.order.dlq";
    public static final String Q_INVENTORY_DLQ = "q.inventory.dlq";
    public static final String Q_PAY_DLQ = "q.pay.dlq";

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
