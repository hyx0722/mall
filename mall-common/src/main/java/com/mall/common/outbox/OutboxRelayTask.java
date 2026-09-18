package com.mall.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * outbox 中继任务：周期投递待发送事件（order / payment / inventory 共用）。
 *
 * 需要业务服务在自己的启动类上开启 {@code @EnableScheduling}，否则本任务不会触发——
 * 失败时没有编译期信号，只会表现为「事件静静地堆在 outbox 表里」。
 * 这正是 {@code mall.outbox.pending} 指标存在的意义，见 OutboxMetrics。
 */
@Slf4j
public class OutboxRelayTask {

    private final OutboxService outboxService;

    public OutboxRelayTask(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 10_000)
    public void relay() {
        try {
            outboxService.relayPending();
        } catch (Exception e) {
            log.error("[outbox] 中继异常", e);
        }
    }
}
