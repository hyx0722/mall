package com.mall.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 日志清扫：每 10 分钟删掉本服务**超过保留期**的日志文件。
 *
 * ── 为什么这不是唯一的清理机制 ────────────────────────────────────────
 * 每个服务的 yml 里还开着 Logback 自己的滚动与保留：
 * <pre>
 *   logging.logback.rollingpolicy:
 *     max-file-size: 10MB      # 到量滚动
 *     max-history: 3           # 最多留 3 个历史文件
 *     total-size-cap: 100MB    # 总量上限
 * </pre>
 * **滚动 + 保留交给 Logback，本任务只做兜底清扫**。分工的理由是个硬约束：
 * 正在写入的日志文件被 Logback 持有句柄，**Windows 上删不掉**（会抛 FileSystemException），
 * 所以指望定时任务去删「当前日志」是做不到的。
 *
 * 本任务靠 mtime 天然避开活动文件：正在写的文件 mtime 一直在刷新，永远不会「过期」；
 * 能命中保留期的绝大多数是滚动出来的历史文件——它们已被关闭，可以安全删除。
 *
 * ── 只清自己的 ────────────────────────────────────────────────────
 * 日志按服务分目录放（{@code logs/<服务名>/}），本任务只扫**自己那个子目录**：
 * 六个服务共用 logs 根目录，若不加限定，先跑的那个会把别人的日志也删了。
 * 分目录之后比「按文件名前缀过滤」更硬——目录边界就是归属边界，不依赖命名约定。
 */
@Slf4j
public class LogCleanupTask {

    private static final String LOG_SUFFIX = ".log";

    private final String logDir;
    private final String appName;

    /** 保留期（分钟）：早于「现在 − 本值」的文件才会被删 */
    @Value("${mall.log.retention-minutes:1440}")
    private long retentionMinutes;

    public LogCleanupTask(String logDir, String appName) {
        this.logDir = logDir;
        this.appName = appName;
    }

    /** 10 分钟一轮；间隔可配，便于本地调试验证 */
    @Scheduled(fixedDelayString = "${mall.log.cleanup-interval-ms:600000}", initialDelay = 60_000)
    public void cleanup() {
        // 服务专属子目录：logs/<appName>/
        Path dir = Paths.get(logDir, appName);
        if (!Files.isDirectory(dir)) {
            // 还没产生过日志（或 LOG_DIR 指向了别处），静默跳过——不是错误
            return;
        }
        Instant deadline = Instant.now().minus(retentionMinutes, ChronoUnit.MINUTES);
        int deleted = 0;
        int failed = 0;
        // 目录已是本服务专属，不必再按文件名筛
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + LOG_SUFFIX + "*")) {
            List<Path> candidates = drain(stream);
            for (Path file : candidates) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(deadline)) {
                        Files.delete(file);
                        deleted++;
                    }
                } catch (IOException e) {
                    // 文件被占用（正在写）或权限不足：跳过并计数，绝不让单个文件中断整轮清扫
                    failed++;
                    log.debug("[log-cleanup] 无法删除 {}：{}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[log-cleanup] 扫描日志目录 {} 失败：{}", dir.toAbsolutePath(), e.getMessage());
            return;
        }
        if (deleted > 0 || failed > 0) {
            log.info("[log-cleanup] 本轮清理 {}：删除 {} 个、跳过 {} 个（保留 {} 分钟内的）",
                    dir.toAbsolutePath(), deleted, failed, retentionMinutes);
        }
    }

    /** 把目录流收成有序列表：先收完再删，避免边遍历边改目录 */
    private static List<Path> drain(DirectoryStream<Path> stream) {
        List<Path> out = new ArrayList<>();
        stream.forEach(out::add);
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }
}
