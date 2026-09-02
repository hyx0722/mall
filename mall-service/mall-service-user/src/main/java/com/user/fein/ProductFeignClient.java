package com.user.fein;


import com.model.bean.Product;
import com.model.bean.Result;
import com.user.fein.fall.ProductFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(value = "mall-service-product",fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    // 返回带 DB 主键的商品，供库存初始化解耦使用
    @PostMapping("/addNumProduct")
    Result<Product> addNumProduct(Product product);
}
