package com.order.fein.fall;

import com.order.fein.InventoryFeignClient;
import org.springframework.stereotype.Component;

@Component
public class InventoryFeignClientFallback implements InventoryFeignClient {
}
