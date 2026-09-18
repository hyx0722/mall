package com.order.feign.fall;

import com.model.bean.Product;
import com.model.bean.Result;
import com.order.feign.ProductFeignClient;
import org.springframework.stereotype.Component;

@Component
public class ProductFeignClientFallback implements ProductFeignClient {

    @Override
    public Result<Product> findProductById(Long id) {
        return Result.error("商品服务暂不可用");
    }
}
