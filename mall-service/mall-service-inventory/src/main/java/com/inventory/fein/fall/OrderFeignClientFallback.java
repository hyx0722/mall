package com.inventory.fein.fall;



import com.inventory.fein.OrderFeignClient;
import org.springframework.stereotype.Component;

@Component
public class OrderFeignClientFallback implements OrderFeignClient {
}
