package com.mall.common.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 管理员操作审计的装配入口。
 *
 * <p>与 CommonWebConfig / OutboxConfig 一样走 {@code @Import} 模式：
 * 需要审计的服务在自己的启动类上导入本类即可，mall-common 本身不被组件扫描。</p>
 *
 * <p>前置条件：classpath 上有 spring-boot-starter-aop（本模块已声明，随依赖传递），
 * 且该服务有自己的 DataSource（审计表建在服务自己的库里）。</p>
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditLogRepository auditLogRepository(
            DataSource dataSource,
            @Value("${spring.application.name:unknown}") String serviceName) {
        return new AuditLogRepository(dataSource, serviceName);
    }

    @Bean
    public AuditLogAspect auditLogAspect(AuditLogRepository auditLogRepository) {
        return new AuditLogAspect(auditLogRepository);
    }

    /**
     * 查询端也随配置注册：导入了本类就同时拥有「写」与「查」，
     * 不会出现某个服务只写不查、或只查不写的半套状态。
     */
    @Bean
    public AuditAdminController auditAdminController(AuditLogRepository auditLogRepository) {
        return new AuditAdminController(auditLogRepository);
    }
}
