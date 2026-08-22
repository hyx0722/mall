package com.user.fein.fall;

import com.model.bean.Product;
import com.model.bean.Result;
import com.user.fein.ProductFeignClient;
import org.springframework.stereotype.Component;

@Component
public class ProductFeignClientFallback implements ProductFeignClient {
    @Override
    public Result addProduct(Product product) {
        return Result.error("添加商品失败");
    }
}
