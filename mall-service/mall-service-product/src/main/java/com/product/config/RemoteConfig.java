package com.product.config;

import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RemoteConfig {
    @Bean
    Retryer retryer(){
    return new Retryer.Default();
}


    @Bean
    Logger.Level feignLoggerLevel(){return Logger.Level.FULL;}

}
