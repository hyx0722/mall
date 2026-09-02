package com.user.controller;


import com.model.bean.Product;
import com.model.bean.Result;
import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;
import com.user.fein.InventoryFeignClient;
import com.user.fein.ProductFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@Slf4j
@Validated
public class UserNumController {

    @Autowired
    ProductFeignClient productFeignClient;
    @Autowired
    InventoryFeignClient inventoryFeignClient;

    //商家添加商品：先建商品(DB 回填主键)，再用回填后的 id 初始化库存；
    //两段远程调用任一失败即抛异常，避免“商品建好了库存却没建”的脏状态
    @PostMapping("userToAddProduct")
    public Result userToAddProduct(@RequestBody @Validated Product product){
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = (Long) map.get("id");
        product.setUserId(userId);

        Result<Product> productResult = productFeignClient.addNumProduct(product);
        if (productResult == null || productResult.getCode() != 0 || productResult.getData() == null
                || productResult.getData().getId() == null) {
            throw new BusinessException(productResult == null ? "商品服务暂不可用" : productResult.getMessage());
        }
        // 用商品服务回填的主键初始化库存
        product.setId(productResult.getData().getId());

        Result inventoryResult = inventoryFeignClient.addNumInventory(product);
        if (inventoryResult == null || inventoryResult.getCode() != 0) {
            throw new BusinessException(inventoryResult == null ? "库存服务暂不可用" : inventoryResult.getMessage());
        }
        return Result.success();
    }

}
