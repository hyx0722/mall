package com.mall.common.audit;

import java.util.regex.Pattern;

/**
 * 审计日志的脱敏与截断工具。
 *
 * <p>审计日志最危险的副产品是「把密码抄进数据库」。本类只处理查询参数：
 * 键名里带 password / pwd / secret / token / authorization 的值一律替换为 {@code ***}。
 * 请求体在 {@link AuditLogAspect} 里根本不会被读取，因此管理员重置密码的
 * {@code newPassword} 不会进入审计表。</p>
 */
public final class AuditLogSanitizer {

    /** 匹配 key=value；key 里可包含前缀（如 newPassword），大小写不敏感 */
    private static final Pattern SENSITIVE_QUERY =
            Pattern.compile("(?i)((?:password|passwd|pwd|secret|token|authorization)=)([^&]*)");

    private AuditLogSanitizer() {
    }

    /**
     * 脱敏查询串。null/空白原样返回；超长由调用方截断。
     */
    public static String query(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        return SENSITIVE_QUERY.matcher(raw).replaceAll("$1***");
    }

    /**
     * 按字符数截断，避免超过数据库列宽导致整条日志丢失。
     */
    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
