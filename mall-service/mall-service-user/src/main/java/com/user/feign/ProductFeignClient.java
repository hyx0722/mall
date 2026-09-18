package com.user.feign;


import com.model.bean.Product;
import com.model.bean.Result;
import com.user.feign.fall.ProductFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "mall-service-product",fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    // 返回带 DB 主键的商品，供库存初始化解耦使用
    @PostMapping("/addNumProduct")
    Result<Product> addNumProduct(Product product);

    // 商家给自家商品发券时校验商品归属：比对返回商品的 userId 与登录态
    @GetMapping("/findProductById")
    Result<Product> findProductById(@RequestParam("id") Long id);
}
