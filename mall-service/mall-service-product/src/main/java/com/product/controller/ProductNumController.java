package com.product.controller;


import com.model.bean.Product;
import com.model.bean.Result;
import com.product.service.ProductNumService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Validated
@Slf4j
public class ProductNumController {
    @Autowired
    ProductNumService productNumService;

    @PostMapping("addNumProduct")
    @Transactional
    public Result addNumProduct(@RequestBody @Validated Product product){
        if (productNumService.findNumProduct(product)==null){
            return Result.error("该商品已存在，添加失败，请在已有商品页面修改");
        }
        productNumService.addNumProduct(product);
        return Result.success();
    }

}
