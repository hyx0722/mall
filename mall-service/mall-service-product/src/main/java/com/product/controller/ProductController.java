package com.product.controller;



import com.model.bean.Product;
import com.model.bean.Result;
import com.product.service.ProductService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
public class ProductController {

    @Autowired
    ProductService productService;

    //根据商品名查找商品
    @GetMapping("/findProductByProductName")
    public Result<List<Product>> findProductByProductName(
            @RequestParam Integer start,
            @RequestParam Integer size,
            @RequestParam String productName
    ){
        List<Product> products=productService.findProductByProductName(start,size,productName);
        return Result.success(products);
    }

    //根据商家名查找商家发布的商品
    @GetMapping("/findProductByUserName")
    public Result<List<Product>> findProductByUserName(
            @RequestParam Integer start,
            @RequestParam Integer size,
            @RequestParam @Pattern(regexp = "^\\S{5,16}$")String username
    ){
        List<Product> products=productService.findProductByUserName(start,size,username);
        return Result.success(products);
    }

    //查看自己发布的商品
    @GetMapping("/findProductByUserId")
    public Result<List<Product>> findProductByUserId(
            @RequestParam Integer start,
            @RequestParam Integer size
    ){
        List<Product> products=productService.findProductByUserId(start,size);
        return Result.success(products);
    }


}
