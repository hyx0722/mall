package com.user.fein.fall;

import com.user.fein.OrderFeignClient;
import org.springframework.stereotype.Component;

@Component
public class OrderFeignClientFallback implements OrderFeignClient {
}
