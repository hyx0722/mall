package com.user.fein;

import com.user.fein.fall.OrderFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(value = "mall-service-order",fallback = OrderFeignClientFallback.class)
public interface OrderFeignClient {
}
