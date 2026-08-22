package com.order.fein;



import com.order.fein.fall.ProductFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-product",fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {
}
