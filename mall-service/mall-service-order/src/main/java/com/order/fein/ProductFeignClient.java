package com.order.fein;

import com.model.bean.Product;
import com.model.bean.Result;
import com.order.fein.fall.ProductFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "mall-service-product",fallback = ProductFeignClientFallback.class)
public interface ProductFeignClient {

    // 下单时同步拉取商品快照（价格/名称/主图），供订单明细落库
    @GetMapping("/findProductById")
    Result<Product> findProductById(@RequestParam("id") Integer id);
}
