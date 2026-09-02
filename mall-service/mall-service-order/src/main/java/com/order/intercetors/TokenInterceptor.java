package com.order.intercetors;

import com.model.util.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 下游服务身份拦截器：鉴权统一由网关(AuthGlobalFilter)完成。
 * 本拦截器仅读取网关注入的 X-User-Id/X-Username 头并写入 ThreadLocal，
 * 供 controller/service 取当前登录用户。头缺失（内部 Feign/公开接口）时直接放行。
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String xUserId = request.getHeader("X-User-Id");
        if (xUserId != null && !xUserId.isBlank()) {
            Map<String, Object> identity = new HashMap<>();
            identity.put("id", Integer.valueOf(xUserId));
            identity.put("username", request.getHeader("X-Username"));
            ThreadLocalUtil.set(identity);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        ThreadLocalUtil.remove();
    }
}
