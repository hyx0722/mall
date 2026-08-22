package com.inventory.fein.fall;

import com.inventory.fein.ProductFeignClient;
import org.springframework.stereotype.Component;

@Component
public class ProductFeignClientFallback implements ProductFeignClient {
}
