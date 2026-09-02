package com.mall.common.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 统一为下游业务服务注册 {@link IdentityInterceptor}：
 * 身份由网关注入 X-User-Id/X-Username，此处仅将其写入 ThreadLocal（缺失时放行）。
 *
 * 原 product/order/inventory 三份本地 WebConfig 收敛至此，各服务启动类 @Import 本类即可：
 * {@code @Import({GlobalExceptionHandler.class, CommonWebConfig.class})}。
 */
@Configuration
public class CommonWebConfig implements WebMvcConfigurer {

    @Bean
    public IdentityInterceptor identityInterceptor() {
        return new IdentityInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(identityInterceptor()).addPathPatterns("/**");
    }
}
