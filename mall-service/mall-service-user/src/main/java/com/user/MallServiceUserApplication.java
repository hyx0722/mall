package com.user;

import com.mall.common.feign.CommonFeignConfig;
import com.mall.common.logging.LogCleanupConfig;
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
// 领券是「条件 UPDATE 抢名额 + 插持有记录」两步，必须同事务（重复领取要整体回滚，否则名额白扣）。
// Boot 在存在 PlatformTransactionManager 时会自动开事务管理，这里显式声明与 order/payment/inventory 保持一致，
// 也免得日后有人把数据源相关自动配置排除掉时 @Transactional 静默失效。
@EnableTransactionManagement
@EnableScheduling
@Import({GlobalExceptionHandler.class, CommonFeignConfig.class, LogCleanupConfig.class})
public class MallServiceUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServiceUserApplication.class,args);
    }
}
