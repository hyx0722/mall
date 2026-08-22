package com.order.fein;


import com.order.fein.fall.InventoryFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-inventory",fallback = InventoryFeignClientFallback.class)
public interface InventoryFeignClient {
}
