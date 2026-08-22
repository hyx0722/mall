package com.inventory.fein;



import com.inventory.fein.fall.ProductFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-product",fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {
}
