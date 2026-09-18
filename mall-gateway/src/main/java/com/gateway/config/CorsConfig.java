package com.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 网关跨域配置。
 *
 * 为什么需要：开发期两个前端走 Vite dev proxy（同源转发）所以从没暴露过 CORS，
 * 但 `npm run build` 的 dist 产物一旦部署到别的域名/端口，浏览器就会拦下所有请求——
 * 而那时候的报错是浏览器侧的 CORS 提示，不是后端日志，很容易误判成接口故障。
 *
 * 两个必须留意的点：
 * 1) **必须排除预检请求的鉴权**。浏览器在跨域 POST 前会先发一个不带 Authorization 的
 *    OPTIONS 预检；CorsWebFilter 是 WebFilter，跑在 Gateway 的过滤器链之前并会直接
 *    短路掉合法预检，因此 AuthGlobalFilter 不会把它当成未登录请求拒掉。
 * 2) **allowed-origins 不能用 `*`**。请求带 Authorization 头（本项目不用 Cookie），
 *    配通配符会与 allowCredentials 冲突而被浏览器整体拒绝；且通配符等于对所有站点开放。
 *    所以这里要求显式列出，默认只放两个前端 dev 端口，部署时用环境变量覆盖。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:5174}") String allowedOrigins) {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 前端自定义头只有 Authorization（裸 JWT）+ Content-Type
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        // 预检结果缓存 1 小时，避免每个请求都多一次 OPTIONS 往返
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
