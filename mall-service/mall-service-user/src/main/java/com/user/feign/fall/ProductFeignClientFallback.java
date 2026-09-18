package com.user.feign.fall;

import com.model.bean.Product;
import com.model.bean.Result;
import com.user.feign.ProductFeignClient;
import org.springframework.stereotype.Component;

@Component
public class ProductFeignClientFallback implements ProductFeignClient {
    @Override
    public Result<Product> addNumProduct(Product product) {
        return Result.error("添加商品失败");
    }
}
