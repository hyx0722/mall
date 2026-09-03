package com.payment;

import com.mall.common.feign.CommonFeignConfig;
import com.mall.common.web.CommonWebConfig;
import com.model.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableTransactionManagement
@EnableScheduling
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, CommonFeignConfig.class})
public class MallServicePaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServicePaymentApplication.class,args);
    }
}
