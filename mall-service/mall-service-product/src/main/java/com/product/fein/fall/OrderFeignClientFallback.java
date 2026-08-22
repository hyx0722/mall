package com.product.fein.fall;


import com.product.fein.OrderFeignClient;
import org.springframework.stereotype.Component;

@Component
public class OrderFeignClientFallback implements OrderFeignClient {
}
