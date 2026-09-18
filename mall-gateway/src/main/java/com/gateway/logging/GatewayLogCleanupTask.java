package com.gateway.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 网关侧的日志清扫：每 10 分钟删掉**本服务**超过保留期的日志文件。
 *
 * ⚠️ 这是 {@code com.mall.common.logging.LogCleanupTask} 的**有意重复实现**。
 * 网关不依赖 mall-common（它是 WebFlux，而 mall-common 面向 servlet/WebMVC，
 * 两者技术栈不通——见 docs/architecture.md 的依赖边界），所以复用不了那份。
 * **改动清理逻辑时两份都要改。**
 *
 * 与业务服务那份同样是「兜底」：真正的滚动与保留由 Logback 的
 * {@code logging.logback.rollingpolicy.*} 负责，本任务只清扫滚出来的历史文件——
 * 正在写入的活动文件在 Windows 上删不掉（句柄未释放），靠 mtime 天然排除在外。
 */
@Component
@Slf4j
public class GatewayLogCleanupTask {

    private final String logDir;
    private final String appName;

    @Value("${mall.log.retention-minutes:1440}")
    private long retentionMinutes;

    public GatewayLogCleanupTask(
            @Value("${mall.log.dir:${LOG_DIR:logs}}") String logDir,
            @Value("${spring.application.name:mall-gateway}") String appName) {
        this.logDir = logDir;
        this.appName = appName;
    }

    @Scheduled(fixedDelayString = "${mall.log.cleanup-interval-ms:600000}", initialDelay = 60_000)
    public void cleanup() {
        // 服务专属子目录：logs/<appName>/ —— 目录边界即归属边界，不必按文件名筛
        Path dir = Paths.get(logDir, appName);
        if (!Files.isDirectory(dir)) {
            return;
        }
        Instant deadline = Instant.now().minus(retentionMinutes, ChronoUnit.MINUTES);
        int deleted = 0;
        int failed = 0;
        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.log*")) {
            stream.forEach(candidates::add);
        } catch (IOException e) {
            log.warn("[log-cleanup] 扫描日志目录 {} 失败：{}", dir.toAbsolutePath(), e.getMessage());
            return;
        }
        for (Path file : candidates) {
            try {
                if (Files.isRegularFile(file)
                        && Files.getLastModifiedTime(file).toInstant().isBefore(deadline)) {
                    Files.delete(file);
                    deleted++;
                }
            } catch (IOException e) {
                // 被占用或权限不足：跳过该文件，不让它中断整轮
                failed++;
                log.debug("[log-cleanup] 无法删除 {}：{}", file.getFileName(), e.getMessage());
            }
        }
        if (deleted > 0 || failed > 0) {
            log.info("[log-cleanup] 本轮清理 {}：删除 {} 个、跳过 {} 个（保留 {} 分钟内的）",
                    dir.toAbsolutePath(), deleted, failed, retentionMinutes);
        }
    }
}
