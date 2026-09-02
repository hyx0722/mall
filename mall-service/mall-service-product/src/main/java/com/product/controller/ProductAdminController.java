package com.product.controller;

import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Product;
import com.model.bean.Result;
import com.product.service.ProductNumService;
import com.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员内部系统：商品管理（查看所有商品、任意上/下架）。
 * 全部接口要求管理员（Auths.requireAdmin）。经网关：/product/admin/listAll 等。
 */
@RestController
@Validated
public class ProductAdminController {

    @Autowired
    private ProductService productService;
    @Autowired
    private ProductNumService productNumService;

    // 分页查看所有商品（含下架，带卖家名），keyword 匹配商品名
    @GetMapping("/admin/listAll")
    public Result<PageBean<Product>> listAll(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Auths.requireAdmin();
        return Result.success(productService.pageAllProducts(page, size, keyword));
    }

    // 对任意商品上/下架：status=1 上架 / 0 下架
    @PutMapping("/admin/shelf")
    public Result shelf(@RequestParam Long id, @RequestParam Integer status) {
        Auths.requireAdmin();
        productNumService.adminChangeStatus(id, status);
        return Result.success();
    }
}
