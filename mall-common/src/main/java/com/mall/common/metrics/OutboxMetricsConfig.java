package com.mall.common.metrics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 注册 {@link OutboxMetrics}。MeterBinder 类型的 Bean 会被 Spring Boot 自动绑定到所有 MeterRegistry，
 * 无需手工 register。
 *
 * 与 CommonWebConfig / CommonFeignConfig 同样走 @Import 模式：
 * 只有带 outbox 表的服务（order / payment）才在自己的启动类上导入本类。
 */
@Configuration
public class OutboxMetricsConfig {

    @Bean
    public OutboxMetrics outboxMetrics(DataSource dataSource) {
        return new OutboxMetrics(dataSource);
    }
}
