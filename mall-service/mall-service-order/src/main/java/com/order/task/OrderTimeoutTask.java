package com.order.task;

import com.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时自动取消定时任务：周期性扫描超过 {@code order.pay-timeout-minutes}
 * （默认 30）分钟未支付的待付款订单并取消（0 -> 4），取消后发 order.canceled 释放锁定库存。
 */
@Component
@Slf4j
public class OrderTimeoutTask {

    @Autowired
    OrderService orderService;

    @Value("${order.pay-timeout-minutes:30}")
    private long timeoutMinutes;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cancelExpiredOrders() {
        try {
            orderService.cancelExpiredOrders(timeoutMinutes);
        } catch (Exception e) {
            log.error("[order] 支付超时取消扫描异常", e);
        }
    }
}
