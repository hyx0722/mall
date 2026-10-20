package com.mall.common.audit;

import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理员审计日志查询。
 *
 * <p>与 {@code OutboxAdminController} 一样，本类在 {@link AuditConfig} 里以 {@code @Bean}
 * 显式注册：各服务只扫自己的包，{@code com.mall.common} 不在扫描路径内，但
 * {@code RequestMappingHandlerMapping} 只看类上有没有 {@code @RestController}。</p>
 *
 * <p>每个服务各自的库里有独立的 {@code audit_log}，所以本接口在 user/product/order/inventory
 * 下各有一份，经网关对外分别是 {@code /user/admin/audit/logs} 等。管理后台按服务查询后聚合。</p>
 */
@RestController
public class AuditAdminController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AuditLogRepository repository;

    public AuditAdminController(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * @param page       页码，从 1 开始
     * @param size       每页条数，钳制在 1..100
     * @param operatorId 操作人ID（可选）
     * @param action     动作名模糊匹配（可选）
     * @param result     1-成功 0-失败（可选）
     * @param from       起始日期 yyyy-MM-dd（含，可选）
     * @param to         结束日期 yyyy-MM-dd（含，可选）
     */
    @GetMapping("/admin/audit/logs")
    public Result<PageBean<AuditLogEntry>> logs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        // 网关只校验 JWT，不做角色判断；这是唯一的角色闸门，不能省。
        Auths.requireAdmin();

        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        String f = normalizeDate(from);
        String t = normalizeDate(to);

        long total = repository.count(operatorId, action, result, f, t);
        if (total == 0L) {
            return Result.success(new PageBean<>(0L, List.of()));
        }
        List<AuditLogEntry> items = repository.page(operatorId, action, result, f, t, (p - 1) * s, s);
        return Result.success(new PageBean<>(total, items));
    }

    /**
     * 日期只接受 yyyy-MM-dd。非法值按「未传」处理而不是抛 500——
     * 日期是筛选条件，不该让整页日志打不开。
     */
    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
