package com.inventory.fein;




import com.inventory.fein.fall.UserFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-user",fallback = UserFeignClientFallback.class)
public interface UserFeignClient {
}
