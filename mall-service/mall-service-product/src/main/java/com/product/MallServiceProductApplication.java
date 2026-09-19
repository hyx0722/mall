package com.product;

import com.mall.common.logging.LogCleanupConfig;
import com.mall.common.web.CommonWebConfig;
import com.model.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
@EnableScheduling
// 商品/分类读路径的缓存。装配细节（含降级策略）见 com.product.config.ProductCacheConfig
@EnableCaching
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, LogCleanupConfig.class})
public class MallServiceProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServiceProductApplication.class,args);
    }
}
