package com.payment.config;

import com.payment.mapper.RefundMapper;
import com.payment.metrics.RefundMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * payment 服务的业务指标装配。
 *
 * 与 mall-common 里的配置不同，本类**不需要**在启动类上 {@code @Import}——
 * 它在 {@code com.payment} 包下，会被 {@code @SpringBootApplication} 的组件扫描直接捡到。
 *
 * {@link RefundMetrics} 是 MeterBinder，会被 Boot 自动绑定到所有 MeterRegistry，
 * 因此这里只负责构造，不需要手工 register。MeterRegistry 由 actuator 提供
 * （payment 的 pom 经 mall-service 继承到了 micrometer-registry-prometheus）。
 */
@Configuration
public class PaymentMetricsConfig {

    /**
     * 阈值与 {@code RefundReconcileTask} 读同一个键，保证「指标说悬挂」与「任务去重投」
     * 判的是同一批单，不会出现指标报警但任务不处理的口径错位。
     */
    @Bean
    public RefundMetrics refundMetrics(RefundMapper refundMapper,
                                       @Value("${payment.refund-reconcile-minutes:10}") int stuckMinutes) {
        return new RefundMetrics(refundMapper, stuckMinutes);
    }
}
