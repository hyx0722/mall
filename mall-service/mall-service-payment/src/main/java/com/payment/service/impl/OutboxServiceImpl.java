package com.payment.service.impl;

import com.model.bean.Outbox;
// Spring Boot 4 的托管 Mapper 是 Jackson 3（tools.jackson），不再是 Jackson 2（com.fasterxml）
import tools.jackson.databind.ObjectMapper;
import com.payment.mapper.OutboxMapper;
import com.payment.service.OutboxService;
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
 * 事务性发件箱实现（与 order 侧同构）：enqueue 在业务事务内写 outbox，relay 定时投递。
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
            props.setExpiration(String.valueOf(row.getDelayMs()));
        }
        Message message = new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), props);
        rabbitTemplate.send(row.getExchange(), row.getRoutingKey(), message);
        log.debug("[outbox] 已投递 id={} -> {} / {}", row.getId(), row.getExchange(), row.getRoutingKey());
    }
}
