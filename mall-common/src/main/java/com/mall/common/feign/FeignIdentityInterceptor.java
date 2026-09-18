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
 * 另外透传原始 Authorization(JWT)：**只有 user 服务需要它**——user 承担登录签发，
 * 鉴权不靠网关注入的 X-User-* 头，而是由自己的 LoginInterceptor 解析 JWT(见
 * mall-service-user 的 WebConfig，该拦截器挂在 /**)。缺了它，任何服务调 user 都会被 401。
 * 其余服务的 CommonWebConfig/IdentityInterceptor 只读 X-User-*，多带此头无副作用。
 *
 * 原 user/order 两处本地 XTokenInterceptor 收敛至此。
 */
public class FeignIdentityInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest request = sra.getRequest();
            // Authorization 与 X-User-* 是两套独立机制，故不共用同一个判空分支
            String authorization = request.getHeader("Authorization");
            if (authorization != null && !authorization.isBlank()) {
                template.header("Authorization", authorization);
            }
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
