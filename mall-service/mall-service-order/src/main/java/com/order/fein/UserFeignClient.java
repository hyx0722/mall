package com.order.fein;




import com.order.fein.fall.UserFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-user",fallback = UserFeignClientFallback.class)
public interface UserFeignClient {
}
