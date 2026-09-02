package com.product.controller;


import com.model.bean.Product;
import com.model.bean.Result;
import com.model.util.ThreadLocalUtil;
import com.product.bean.UpdateProductRequest;
import com.product.service.ProductNumService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@Validated
@Slf4j
public class ProductNumController {
    @Autowired
    ProductNumService productNumService;

    //商家上架商品：同 user_id+name 重复上架显式报错；唯一键在并发下兜底
    @PostMapping("/addNumProduct")
    public Result addNumProduct(@RequestBody @Validated Product product){
        if (productNumService.findNumProductByUserIdAndName(product.getUserId(), product.getName()) != null){
            return Result.error("该商品已存在，请在已有商品页面修改");
        }
        try {
            productNumService.addNumProduct(product);
        } catch (DuplicateKeyException e) {
            //并发双击等场景命中唯一键
            return Result.error("该商品已存在，请在已有商品页面修改");
        }
        return Result.success();
    }

    //商家编辑自己的商品（部分更新），归属以登录态 user_id 为准
    @PutMapping("/updateProduct")
    public Result updateProduct(@RequestBody @Valid UpdateProductRequest request) {
        productNumService.updateProduct(currentUserId(), request);
        return Result.success();
    }

    //商家上/下架自己的商品
    @PutMapping("/shelfProduct")
    public Result shelfProduct(@RequestParam Integer id, @RequestParam Integer status) {
        productNumService.changeProductStatus(currentUserId(), id, status);
        return Result.success();
    }

    private Integer currentUserId() {
        Map<String, Object> map = ThreadLocalUtil.get();
        return (map == null) ? null : (Integer) map.get("id");
    }
}
