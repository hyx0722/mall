package com.user.fein;



import com.user.fein.fall.InventoryFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-inventory",fallback = InventoryFeignClientFallback.class)
public interface InventoryFeignClient {
}
