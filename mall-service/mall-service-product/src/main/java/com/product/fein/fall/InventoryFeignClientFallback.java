package com.product.fein.fall;


import com.product.fein.InventoryFeignClient;
import org.springframework.stereotype.Component;

@Component
public class InventoryFeignClientFallback implements InventoryFeignClient {
}
