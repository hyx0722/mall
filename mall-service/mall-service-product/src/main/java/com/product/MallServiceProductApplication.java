package com.product;

import com.mall.common.web.CommonWebConfig;
import com.model.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDiscoveryClient
@EnableTransactionManagement
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
public class MallServiceProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServiceProductApplication.class,args);
    }
}
