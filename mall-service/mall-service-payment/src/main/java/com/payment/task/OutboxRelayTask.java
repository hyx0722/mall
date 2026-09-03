package com.payment.task;

import com.payment.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox 中继任务：周期投递 pay.success 等待发送事件。
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
            log.error("[payment] outbox 中继异常", e);
        }
    }
}
