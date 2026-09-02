package com.user;

import com.model.web.GlobalExceptionHandler;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Import(GlobalExceptionHandler.class)
@GlobalTransactional
public class MallServiceUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServiceUserApplication.class,args);
    }
}
