package com.mall.common.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志清扫任务的装配。与 CommonWebConfig / OutboxConfig 同样走 @Import 模式
 * （业务服务不扫描 com.mall.common 包）。
 *
 * 前置条件：启动类上要有 {@code @EnableScheduling}——漏了不会有任何编译期信号，
 * 只表现为「日志永远不被清理」。
 *
 * ⚠️ **网关不用本类**（它不依赖 mall-common，见 docs/architecture.md 的依赖边界），
 * 网关有一份等价的 {@code com.gateway.logging.GatewayLogCleanupTask}。
 * 改动清理逻辑时两处都要改。
 */
@Configuration
public class LogCleanupConfig {

    /**
     * @param logDir  日志目录；`mall.log.dir` 优先，其次环境变量 `LOG_DIR`，最后回落相对路径 logs
     * @param appName 服务名，用于**只清自己的**日志（六个服务共用 logs 目录）
     */
    @Bean
    public LogCleanupTask logCleanupTask(
            @Value("${mall.log.dir:${LOG_DIR:logs}}") String logDir,
            @Value("${spring.application.name:application}") String appName) {
        return new LogCleanupTask(logDir, appName);
    }
}
