package com.mall.common.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 审计日志脱敏的守卫测试。
 *
 * <p>审计日志最危险的失败模式不是「没记」，而是「把密码记下来了」：
 * 管理员重置密码的请求体虽然不落库，但查询参数、URL 里仍可能出现 token/secret。
 * 这个测试守的是「敏感键的值永远不进入 audit_log」。</p>
 */
class AuditLogSanitizerTest {

    @Test
    @DisplayName("常见敏感键的值被替换为 ***")
    void masksSensitiveKeys() {
        String raw = "id=7&password=abc123&newPassword=xyz&token=eyJhbGciOi&secret=s3cr3t&authorization=Bearer%20x";
        String masked = AuditLogSanitizer.query(raw);

        assertEquals(false, masked.contains("abc123"), "password 的值不能保留");
        assertEquals(false, masked.contains("xyz"), "newPassword 的值不能保留");
        assertEquals(false, masked.contains("eyJhbGciOi"), "token 的值不能保留");
        assertEquals(false, masked.contains("s3cr3t"), "secret 的值不能保留");
        assertEquals(false, masked.contains("Bearer"), "authorization 的值不能保留");
        assertEquals("id=7&password=***&newPassword=***&token=***&secret=***&authorization=***", masked);
    }

    @Test
    @DisplayName("普通参数原样保留")
    void keepsNormalParams() {
        assertEquals("page=1&size=20&status=1", AuditLogSanitizer.query("page=1&size=20&status=1"));
    }

    @Test
    @DisplayName("null / 空串不抛异常")
    void handlesBlank() {
        assertNull(AuditLogSanitizer.query(null));
        assertEquals("", AuditLogSanitizer.query(""));
        assertEquals("  ", AuditLogSanitizer.query("  "));
    }

    @Test
    @DisplayName("超长内容按列宽截断")
    void truncates() {
        String value = "x".repeat(120);
        assertEquals(100, AuditLogSanitizer.truncate(value, 100).length());
        assertEquals("abc", AuditLogSanitizer.truncate("abc", 100));
        assertNull(AuditLogSanitizer.truncate(null, 100));
    }
}
