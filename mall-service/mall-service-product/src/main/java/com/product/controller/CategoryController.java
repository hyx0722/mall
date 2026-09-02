package com.product.controller;

import com.mall.common.web.Auths;
import com.model.bean.Result;
import com.product.bean.Category;
import com.product.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品分类。查询公开（买家浏览/卖家选品下拉）；增/改仅管理员（内部系统分类管理）。
 * 经网关访问：/product/category/list 等（StripPrefix=1 后映射到 /category/*）。
 */
@RestController
@Validated
public class CategoryController {

    @Autowired
    CategoryService categoryService;

    // 浏览：某父分类下的启用子分类（parentId=0 顶级）
    @GetMapping("/category/list")
    public Result<List<Category>> listByParent(@RequestParam(required = false, defaultValue = "0") Long parentId) {
        return Result.success(categoryService.listEnabledByParent(parentId));
    }

    // 浏览：完整启用分类树
    @GetMapping("/category/tree")
    public Result<List<Category>> tree() {
        return Result.success(categoryService.tree());
    }

    @PostMapping("/category/add")
    public Result<Category> add(@RequestBody @Valid Category category) {
        Auths.requireAdmin();
        return Result.success(categoryService.add(category));
    }

    @PutMapping("/category/update")
    public Result update(@RequestBody @Valid Category category) {
        Auths.requireAdmin();
        categoryService.update(category);
        return Result.success();
    }
}
