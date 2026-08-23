package com.user.fein;


import com.model.bean.Product;
import com.model.bean.Result;
import com.user.fein.fall.ProductFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(value = "mall-service-product",fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    @PostMapping("addNumProduct")
    Result addNumProduct(Product product);
}
