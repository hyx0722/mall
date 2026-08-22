package com.inventory.fein;


import com.inventory.fein.fall.OrderFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-order",fallback = OrderFeignClientFallback.class)
public interface OrderFeignClient {
}
