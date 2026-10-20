package com.mall.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员操作审计标记。
 *
 * <p>标在 Controller 上表示「该控制器的写操作需要审计」；标在方法上可给出更友好的动作名。
 * GET / HEAD / OPTIONS 不记录——审计关注「谁改了什么」，不关注查询。</p>
 *
 * <p>记录动作的是 {@link AuditLogAspect}，落库的是 {@link AuditLogRepository}。
 * 注解本身不做任何事，漏标只是少一条日志，不会影响业务；这也是刻意的：
 * 审计失败/漏记不应该阻塞交易。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 动作名，如「修改用户」「审核退款」；为空时回退到 Controller 方法名 */
    String value() default "";

    /** 对象类型，如 USER / PRODUCT / ORDER；为空时回退到 Controller 类名 */
    String target() default "";
}
