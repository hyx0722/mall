package com.order.task;

import com.order.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox 中继任务：周期投递待发送事件（事务提交后由 outbox 表可靠兜底）。
 */
@Component
@Slf4j
public class OutboxRelayTask {

    @Autowired
    OutboxService outboxService;

    @Scheduled(fixedDelay = 3000, initialDelay = 10_000)
    public void relay() {
        try {
            outboxService.relayPending();
        } catch (Exception e) {
            log.error("[order] outbox 中继异常", e);
        }
    }
}
