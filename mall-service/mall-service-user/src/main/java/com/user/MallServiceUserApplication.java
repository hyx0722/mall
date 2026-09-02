package com.user;

import com.mall.common.feign.CommonFeignConfig;
import com.model.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import({GlobalExceptionHandler.class, CommonFeignConfig.class})
public class MallServiceUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServiceUserApplication.class,args);
    }
}
