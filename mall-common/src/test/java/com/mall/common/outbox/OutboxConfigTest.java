package com.mall.common.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * outbox 装配的守卫测试。
 *
 * 为什么需要它：{@link OutboxConfig} 是否正确，**编译期完全看不出来**——
 * 少写一个 @Bean、启动类上忘了 @Import、或者把实现直接 new 出来而不交给容器，
 * 都会让 @Transactional 静默失效。而失效的后果是丢事件：订单不推进、库存不释放，
 * 且没有任何外部表征（这正是 mall.outbox.pending 指标要观测的那种静默故障）。
 *
 * 这里只用桩依赖（不连库、不连 broker）验证「装配」与「代理」两件事——
 * 它们都不需要真实中间件，因此可以在任何机器上跑。
 */
class OutboxConfigTest {

    /** 真实服务里 DataSource/RabbitTemplate/ObjectMapper 由 Spring Boot 自动配置提供 */
    @Configuration
    @EnableTransactionManagement
    static class StubCollaborators {

        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return mock(ObjectMapper.class);
        }
    }

    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(StubCollaborators.class, OutboxConfig.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @DisplayName("OutboxConfig 同时导出 OutboxService 与 relay 任务")
    void exposesServiceAndRelayTask() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            assertNotNull(ctx.getBean(OutboxService.class), "缺少 OutboxService，各服务注入会失败");
            assertNotNull(ctx.getBean(OutboxRelayTask.class),
                    "缺少 OutboxRelayTask，outbox 将无人投递（事件静静堆在表里）");
        }
    }

    @Test
    @DisplayName("OutboxService 必须是事务代理，否则 enqueueNewTx 的 REQUIRES_NEW 静默失效")
    void serviceIsTransactionalProxy() {
        try (AnnotationConfigApplicationContext ctx = context()) {
            OutboxService service = ctx.getBean(OutboxService.class);

            assertTrue(AopUtils.isAopProxy(service),
                    "必须是 AOP 代理：否则 enqueueNewTx(REQUIRES_NEW) 不生效，"
                            + "库存扣减失败回执会随业务事务一起回滚，订单永远收不到 deduct_failed");
        }
    }
}
