package com.mall.common.audit;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条管理员操作审计记录。
 *
 * <p>只记查询参数（{@code queryString}），**不记请求体**：管理员接口的请求体里常有
 * 新密码、密钥等敏感字段，记下来就等于把凭据写进了数据库。查询参数也会经过
 * {@link AuditLogSanitizer} 脱敏。</p>
 */
@Data
public class AuditLogEntry {

    private Long id;

    /** 操作人用户ID；0 表示系统或未识别 */
    private Long operatorId;

    private String operatorName;

    /** 操作人角色：1-普通用户，2-管理员 */
    private Integer operatorRole;

    /** 服务名（spring.application.name），用于区分日志来自哪个库 */
    private String service;

    /** 动作名，如「修改用户」 */
    private String action;

    /** 对象类型：USER / PRODUCT / ORDER / COUPON / OUTBOX... */
    private String targetType;

    /** 对象ID（从请求参数或 DTO 的 getId 一类方法尽力提取） */
    private String targetId;

    /** HTTP 方法 */
    private String method;

    /** 请求路径（已 StripPrefix 后的服务内路径） */
    private String path;

    /** 脱敏后的查询参数；请求体不记录 */
    private String params;

    /** 1-成功，0-失败 */
    private Integer result;

    /** 失败原因（异常 message，截断） */
    private String errorMsg;

    private String ip;

    private String userAgent;

    private LocalDateTime createdTime;
}
