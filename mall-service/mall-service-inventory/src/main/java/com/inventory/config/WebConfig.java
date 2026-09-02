package com.inventory.config;

import com.inventory.intercetors.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 身份由网关注入 X-User-Id/X-Username；此处仅将其写入 ThreadLocal（缺失时放行）
        registry.addInterceptor(tokenInterceptor).addPathPatterns("/**");
    }
}
