package com.order.fein.fall;
import com.order.fein.ProductFeignClient;
import org.springframework.stereotype.Component;

@Component
public class ProductFeignClientFallback implements ProductFeignClient {
}
