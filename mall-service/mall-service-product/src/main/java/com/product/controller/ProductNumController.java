package com.product.controller;


import com.model.bean.Product;
import com.model.bean.Result;
import com.product.service.ProductNumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@Validated
public class ProductNumController {
    @Autowired
    ProductNumService productNumService;

    @PostMapping("addProduct")
    @Transactional
    public Result addProduct(@Validated Product product){
        productNumService.addProduct(product);
        return Result.success();
    }

}
