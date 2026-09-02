package com.gateway.config;

import com.gateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 网关统一鉴权：
 * 1) 白名单 /user/login、/user/register 直接放行；
 * 2) 其余请求解析 Authorization(JWT) + 校验 Redis 登录态是否仍有效；
 * 3) 通过后剥离入站伪造的 X-User-Id/X-Username，再注入真实用户身份头，
 *    供下游 order/product/inventory 读取写入 ThreadLocal。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST =
            List.of("/user/login", "/user/register",
                    // 第三方支付异步回调：无 JWT 登录态（验签在支付服务内部完成）
                    "/pay/alipay/notify", "/pay/wx/notify");

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (WHITE_LIST.contains(path)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        try {
            Map<String, Object> claims = jwtUtil.parseToken(token);
            Object idObj = claims.get("id");
            if (idObj == null) {
                throw new RuntimeException("token 缺少用户 id");
            }
            long id = ((Number) idObj).longValue();
            String username = (String) claims.get("username");
            // 角色取自 claims，旧 token 无 role 视为普通用户 1
            int role = claims.get("role") instanceof Number r ? r.intValue() : 1;

            // 校验登录态仍有效（单设备登录 / 主动失效）
            String redisToken = stringRedisTemplate.opsForValue().get("login:token:" + id);
            if (redisToken == null || !redisToken.equals(token)) {
                throw new RuntimeException("登录态已失效");
            }

            // 剥离伪造头，注入真实身份
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-Username");
                        h.remove("X-User-Role");
                    })
                    .headers(h -> {
                        h.set("X-User-Id", String.valueOf(id));
                        h.set("X-Username", username);
                        h.set("X-User-Role", String.valueOf(role));
                    })
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        byte[] bytes = "{\"code\":401,\"message\":\"未登录或登录态失效\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 最先执行
    }
}
