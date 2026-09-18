package com.mall.common.outbox;

import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 事务性发件箱的装配入口。
 *
 * 与 CommonWebConfig / CommonFeignConfig / OutboxMetricsConfig 同样走 @Import 模式
 * （业务服务不扫描 com.mall.common 包）。**只有带 outbox 表的服务**
 * （order / payment / inventory）才在自己的启动类上导入本类。
 *
 * 前置条件（缺一即失效，且都是启动期/运行期才暴露）：
 * - 该服务的库里存在 outbox 表（见各自 *.sql）；
 * - 启动类上有 @EnableScheduling，否则 OutboxRelayTask 不会被触发；
 * - classpath 上有 spring-boot-starter-amqp（三个使用者本就有）。
 */
@Configuration
public class OutboxConfig {

    @Bean
    public OutboxService outboxService(DataSource dataSource, RabbitTemplate rabbitTemplate,
                                       ObjectMapper objectMapper) {
        return new OutboxServiceImpl(dataSource, rabbitTemplate, objectMapper);
    }

    @Bean
    public OutboxRelayTask outboxRelayTask(OutboxService outboxService) {
        return new OutboxRelayTask(outboxService);
    }
}
