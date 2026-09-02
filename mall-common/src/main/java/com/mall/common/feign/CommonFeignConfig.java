package com.mall.common.feign;

import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一 Feign 客户端配置：默认重试策略 + 全量日志 + 身份头透传。
 * 原 user/product/order/inventory 四份本地 RemoteConfig 收敛至此，
 * 需要发起 Feign 调用的服务(user/order)在启动类 @Import 本类。
 */
@Configuration
public class CommonFeignConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default();
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public FeignIdentityInterceptor feignIdentityInterceptor() {
        return new FeignIdentityInterceptor();
    }
}
