package com.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启订单侧定时任务（支付超时自动取消扫描等）。 */
@Configuration
@EnableScheduling
public class OrderScheduleConfig {
}
