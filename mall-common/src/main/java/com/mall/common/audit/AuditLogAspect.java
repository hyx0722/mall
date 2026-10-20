package com.mall.common.audit;

import com.mall.common.web.Auths;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 管理员写操作审计切面。
 *
 * <p>为什么用 AOP 而不是 HandlerInterceptor：业务异常会被 {@code @RestControllerAdvice}
 * 解析成 HTTP 200 + {@code Result.error}，等到 {@code afterCompletion} 时异常已经被消费，
 * 拦截器分不清成功与失败。切面包在 controller 外层，能直接看到抛出的异常。</p>
 *
 * <p>三条硬约束：</p>
 * <ul>
 *   <li><b>只记写操作</b>：GET/HEAD/OPTIONS 直接放行；审计关注变更，不关注查询。</li>
 *   <li><b>只记 /admin/ 路径</b>：类上的注解也可能覆盖到商家接口，这里再用路径兜一层。</li>
 *   <li><b>审计失败绝不阻塞业务</b>：落库异常被吞掉并打 WARN，交易照常返回。</li>
 * </ul>
 */
@Slf4j
@Aspect
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogAspect {

    private static final String[] PARAM_KEYS = {
            "id", "userId", "orderId", "productId", "couponId", "withdrawId", "refundNo", "payNo"
    };

    private static final String[] ID_GETTERS = {
            "getId", "getUserId", "getOrderId", "getProductId", "getCouponId",
            "getWithdrawId", "getRefundNo", "getPayNo"
    };

    private final AuditLogRepository repository;

    public AuditLogAspect(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Around("@within(com.mall.common.audit.AuditLog) || @annotation(com.mall.common.audit.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null || isReadOnly(request.getMethod()) || !isAdminPath(request)) {
            return joinPoint.proceed();
        }

        try {
            Object result = joinPoint.proceed();
            record(joinPoint, request, 1, null);
            return result;
        } catch (Throwable t) {
            record(joinPoint, request, 0, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            throw t;
        }
    }

    /**
     * 记录一条审计日志。任何异常都在这里被吞掉——审计是旁路，不能反过来让业务失败。
     */
    private void record(ProceedingJoinPoint joinPoint, HttpServletRequest request, int result, String errorMsg) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Class<?> targetClass = joinPoint.getTarget() == null
                    ? method.getDeclaringClass() : joinPoint.getTarget().getClass();

            AuditLog methodAnn = AnnotatedElementUtils.findMergedAnnotation(method, AuditLog.class);
            AuditLog classAnn = AnnotatedElementUtils.findMergedAnnotation(targetClass, AuditLog.class);

            AuditLogEntry entry = new AuditLogEntry();
            entry.setOperatorId(operatorId(request));
            entry.setOperatorName(operatorName(request));
            entry.setOperatorRole(operatorRole(request));
            entry.setService(repository.getServiceName());
            entry.setAction(actionOf(methodAnn, method));
            entry.setTargetType(targetTypeOf(methodAnn, classAnn, targetClass));
            entry.setTargetId(targetId(joinPoint, request));
            entry.setMethod(request.getMethod());
            entry.setPath(AuditLogSanitizer.truncate(request.getRequestURI(), 255));
            entry.setParams(AuditLogSanitizer.truncate(
                    AuditLogSanitizer.query(request.getQueryString()), 1000));
            entry.setResult(result);
            entry.setErrorMsg(AuditLogSanitizer.truncate(errorMsg, 500));
            entry.setIp(AuditLogSanitizer.truncate(clientIp(request), 64));
            entry.setUserAgent(AuditLogSanitizer.truncate(request.getHeader("User-Agent"), 255));

            repository.insert(entry);
        } catch (Throwable t) {
            log.warn("[audit] 写审计日志失败（业务不受影响）path={}：{}",
                    request.getRequestURI(), t.getMessage());
        }
    }

    private String actionOf(AuditLog methodAnn, Method method) {
        if (methodAnn != null && !methodAnn.value().isBlank()) {
            return AuditLogSanitizer.truncate(methodAnn.value(), 100);
        }
        return AuditLogSanitizer.truncate(method.getName(), 100);
    }

    private String targetTypeOf(AuditLog methodAnn, AuditLog classAnn, Class<?> targetClass) {
        if (methodAnn != null && !methodAnn.target().isBlank()) {
            return AuditLogSanitizer.truncate(methodAnn.target(), 32);
        }
        if (classAnn != null && !classAnn.target().isBlank()) {
            return AuditLogSanitizer.truncate(classAnn.target(), 32);
        }
        return AuditLogSanitizer.truncate(targetClass.getSimpleName(), 32);
    }

    /**
     * 对象ID：先看查询参数，再看参数对象里的常见 getter。
     * 只做「尽力而为」——取不到就留空，绝不为了取 ID 去解析请求体。
     */
    private String targetId(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        for (String key : PARAM_KEYS) {
            String value = request.getParameter(key);
            if (value != null && !value.isBlank()) {
                return AuditLogSanitizer.truncate(value, 64);
            }
        }
        for (Object arg : joinPoint.getArgs()) {
            if (arg == null) {
                continue;
            }
            for (String getter : ID_GETTERS) {
                try {
                    Object value = arg.getClass().getMethod(getter).invoke(arg);
                    if (value != null && !String.valueOf(value).isBlank()) {
                        return AuditLogSanitizer.truncate(String.valueOf(value), 64);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 该参数没有这个 getter / 不可访问：换下一个
                }
            }
        }
        return null;
    }

    private Long operatorId(HttpServletRequest request) {
        Long id = parseLong(request.getHeader("X-User-Id"));
        if (id == null) {
            id = Auths.currentUserId();
        }
        return id == null ? 0L : id;
    }

    private String operatorName(HttpServletRequest request) {
        String name = request.getHeader("X-Username");
        if (name == null || name.isBlank()) {
            name = Auths.currentUsername();
        }
        return AuditLogSanitizer.truncate(name, 64);
    }

    private Integer operatorRole(HttpServletRequest request) {
        Integer role = parseInt(request.getHeader("X-User-Role"));
        if (role == null) {
            role = Auths.currentRole();
        }
        return role == null ? 0 : role;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isReadOnly(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isAdminPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/admin/");
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
