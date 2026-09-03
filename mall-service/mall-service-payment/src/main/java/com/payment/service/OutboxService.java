package com.payment.service;

/**
 * 事务性发件箱：pay.success 与支付落库同事务入库，relay 定时投递到 RabbitMQ（order 侧消费）。
 */
public interface OutboxService {

    /** 事件入箱（须在业务 @Transactional 方法内调用）。 */
    void enqueue(String exchange, String routingKey, Long delayMs, Object payload);

    /** relay：领取待发送行并投递，成功后置已发送。 */
    void relayPending();
}
