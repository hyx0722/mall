package com.product.fein;



import com.product.fein.fall.UserFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-user",fallback = UserFeignClientFallback.class)
public interface UserFeignClient {
}
