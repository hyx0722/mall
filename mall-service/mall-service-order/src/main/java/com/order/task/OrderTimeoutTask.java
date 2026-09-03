package com.order.task;

import com.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时取消的「对账兜底」：主路径已改为下单时投递延迟消息（DLX + per-message TTL），
 * 本任务低频补扫，兜住延迟消息丢失/进程宕机窗口，保证超时订单最终会被取消并释放库存。
 */
@Component
@Slf4j
public class OrderTimeoutTask {

    @Autowired
    OrderService orderService;

    @Value("${order.pay-timeout-minutes:30}")
    private long timeoutMinutes;

    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void cancelExpiredOrders() {
        try {
            orderService.cancelExpiredOrders(timeoutMinutes);
        } catch (Exception e) {
            log.error("[order] 支付超时对账扫描异常", e);
        }
    }
}
