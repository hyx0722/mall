package com.order.service;

/**
 * 事务性发件箱：业务方法与事件发布同事务入库，relay 定时投递到 RabbitMQ。
 */
public interface OutboxService {

    /**
     * 事件入箱（须在业务 @Transactional 方法内调用，与状态变更同一事务提交）。
     *
     * @param exchange    目标交换机
     * @param routingKey  目标路由键
     * @param delayMs     延迟毫秒；非空则经延迟交换机带 per-message TTL
     * @param payload     事件对象（序列化为 JSON 原文入库，relay 原样发送）
     */
    void enqueue(String exchange, String routingKey, Long delayMs, Object payload);

    /** relay：领取待发送行并投递，成功后置已发送（有界循环，供 @Scheduled 任务调用） */
    void relayPending();
}
