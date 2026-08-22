package com.product.fein;

import com.product.fein.fall.OrderFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-order",fallback = OrderFeignClientFallback.class)
public interface OrderFeignClient {
}
