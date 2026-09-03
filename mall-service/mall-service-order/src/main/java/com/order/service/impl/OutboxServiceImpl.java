package com.order.service.impl;

import com.model.bean.Outbox;
// Spring Boot 4 的托管 Mapper 是 Jackson 3（tools.jackson），不再是 Jackson 2（com.fasterxml）
import tools.jackson.databind.ObjectMapper;
import com.order.mapper.OutboxMapper;
import com.order.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 事务性发件箱实现：
 * - enqueue 在业务事务内序列化事件为 JSON 并写 outbox 行（与订单状态同一事务）；
 * - relayPending 领取待发送行，逐个发到 RabbitMQ，成功后置已发送。
 * 由于入箱与业务同库同事务，彻底消除「业务落库了、事件却因进程崩溃没发出去」的窗口。
 */
@Service
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private static final int BATCH = 50;

    @Autowired
    OutboxMapper outboxMapper;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    ObjectMapper objectMapper;

    @Override
    public void enqueue(String exchange, String routingKey, Long delayMs, Object payload) {
        try {
            Outbox row = new Outbox();
            row.setExchange(exchange);
            row.setRoutingKey(routingKey);
            row.setDelayMs(delayMs);
            row.setStatus(0);
            row.setRetryCount(0);
            row.setPayload(objectMapper.writeValueAsString(payload));
            outboxMapper.insertOutbox(row);
        } catch (Exception e) {
            // 入箱失败说明事件源不可靠，抛出让业务事务回滚（宁可不下单也不丢事件）
            throw new IllegalStateException("[outbox] 事件入箱失败 exchange=" + exchange + " key=" + routingKey, e);
        }
    }

    @Override
    @Transactional
    public void relayPending() {
        List<Outbox> pending = outboxMapper.selectPending(BATCH);
        for (Outbox row : pending) {
            try {
                send(row);
                outboxMapper.markSent(row.getId());
            } catch (Exception e) {
                // 单行失败不影响其它行；置重试计数后留待下轮（status 仍为 0）
                outboxMapper.markRetried(row.getId());
                log.error("[outbox] 投递失败 id={} exchange={} key={}", row.getId(), row.getExchange(), row.getRoutingKey(), e);
            }
        }
    }

    private void send(Outbox row) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        if (row.getDelayMs() != null && row.getDelayMs() > 0) {
            // 延迟消息：per-message TTL，Rabbit 到点后由持有队列死信到目标交换机
            props.setExpiration(String.valueOf(row.getDelayMs()));
        }
        Message message = new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), props);
        rabbitTemplate.send(row.getExchange(), row.getRoutingKey(), message);
        log.debug("[outbox] 已投递 id={} -> {} / {}", row.getId(), row.getExchange(), row.getRoutingKey());
    }
}
