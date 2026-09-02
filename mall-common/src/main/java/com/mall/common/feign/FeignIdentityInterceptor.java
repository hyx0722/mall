package com.mall.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器：把当前入站请求(经网关注入)的 X-User-Id/X-Username
 * 透传给下游服务，使服务间调用也携带用户身份。若无上下文则直接放行。
 *
 * 原 user/order 两处本地 XTokenInterceptor 收敛至此。
 */
public class FeignIdentityInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest request = sra.getRequest();
            String userId = request.getHeader("X-User-Id");
            String username = request.getHeader("X-Username");
            String role = request.getHeader("X-User-Role");
            if (userId != null && !userId.isBlank()) {
                template.header("X-User-Id", userId);
                template.header("X-Username", username);
                if (role != null && !role.isBlank()) {
                    template.header("X-User-Role", role);
                }
            }
        }
    }
}
