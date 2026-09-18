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

    /**
     * 商品服务不可用时**必须失败**而不是放行：建券的归属校验依赖它，
     * 降级成「校验通过」等于让任何商家都能给别人的商品发券。
     */
    @Override
    public Result<Product> findProductById(Long id) {
        return Result.error("商品服务暂不可用，请稍后重试");
    }
}
