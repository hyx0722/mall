package com.mall.common.outbox;

/**
 * 事务性发件箱：业务方法与事件发布同事务入库，relay 定时投递到 RabbitMQ。
 *
 * 由 order / payment / inventory 三个服务共用（各库各有一张同名 outbox 表）。
 * 使用方在自己的启动类上 {@code @Import(OutboxConfig.class)} 即可，无需组件扫描。
 */
public interface OutboxService {

    /**
     * 事件入箱（须在业务 @Transactional 方法内调用，与状态变更同一事务提交）。
     *
     * @param exchange   目标交换机
     * @param routingKey 目标路由键
     * @param delayMs    延迟毫秒；非空则经延迟交换机带 per-message TTL
     * @param payload    事件对象（序列化为 JSON 原文入库，relay 原样发送）
     */
    void enqueue(String exchange, String routingKey, Long delayMs, Object payload);

    /**
     * 在**独立事务**中入箱（REQUIRES_NEW）：用于「业务事务注定回滚，但事件仍必须送达」的场景。
     *
     * 目前唯一的用例是库存扣减失败回执 inventory.deduct_failed——它产生于 lockForOrder 抛
     * StockLockException、事务已回滚之后。此时若沿用 {@link #enqueue} 的默认传播，
     * 入箱会挂在同一个即将回滚的事务里被一并撤销，订单永远收不到失败回执；
     * 而丢这条回执会开出超卖窗口（库存没锁上，订单侧只校验状态不校验库存，买家仍可支付）。
     *
     * @see #enqueue 参数含义同上
     */
    void enqueueNewTx(String exchange, String routingKey, Long delayMs, Object payload);

    /** relay：领取待发送行并投递，成功后置已发送（有界循环，供 @Scheduled 任务调用） */
    void relayPending();
}
