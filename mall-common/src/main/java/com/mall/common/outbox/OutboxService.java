package com.mall.common.outbox;

import java.util.List;

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

    /*
     * 以下三个方法由 OutboxConfirmInstaller 装到 RabbitTemplate 上的
     * ConfirmCallback / ReturnsCallback 调用，业务代码**不要直接调**。
     * 都按 outbox 行 id 定位，且都在单条 UPDATE 内完成，因此天然幂等、可重入。
     */

    /** 发布确认：broker 已确认接收 → 置已发送（仅当仍是待发送，重复 ack 无副作用） */
    void markDelivered(Long id);

    /** 发布确认：broker 拒绝(nack) → 累加重试计数并保持待发送（可能是暂时故障，故无限重试） */
    void markRejected(Long id, String cause);

    /** 消息被退回（mandatory 命中，交换机无队列可路由）→ 累加计数，超上限置「已放弃」 */
    void markUnroutable(Long id, String cause);

    /*
     * 以下两个方法**仅供管理端**（OutboxAdminController，/admin/outbox/**，管理员才可调用）。
     * 它们不参与业务链路，也不要放进 relay。
     */

    /**
     * 列出已放弃（status=3）的行，按 id 升序，最多 limit 条。
     *
     * 「已放弃」只可能由路由键与队列绑定不匹配导致（连接异常/nack 走的是无限重试那条路），
     * 修好绑定前重投也不会成功，所以列表里带上 exchange/routingKey 供核对。
     */
    List<AbandonedOutbox> listAbandoned(int limit);

    /**
     * 把已放弃的行重投：status 3 → 0 并清零 retry_count，下一轮 relay（3s 内）会自动领取。
     *
     * 幂等性来自 SQL 的 {@code where status=3} 条件——重复调用影响 0 行，不会把同一批翻两次；
     * 有界性来自 {@code limit}，一次调用不可能把整个积压翻过来冲垮 broker。
     * 不会碰 status=0/1 的活跃行，也不会改动 created_time（保留原始入箱时间）。
     *
     * @param limit 本次最多重投多少条
     * @return 本次**实际**重投的条数（重复调用返回 0）
     */
    int requeueAbandoned(int limit);
}
