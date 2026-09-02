package com.product.controller;

import com.model.bean.Result;
import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;
import com.product.bean.Category;
import com.product.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品分类。查询公开；增/改需登录（现状无商家/管理员角色，登录即可）。
 * 经网关访问：/product/category/list 等（StripPrefix=1 后映射到 /category/*）。
 */
@RestController
@Validated
public class CategoryController {

    @Autowired
    CategoryService categoryService;

    // 浏览：某父分类下的启用子分类（parentId=0 顶级）
    @GetMapping("/category/list")
    public Result<List<Category>> listByParent(@RequestParam(required = false, defaultValue = "0") Integer parentId) {
        return Result.success(categoryService.listEnabledByParent(parentId));
    }

    // 浏览：完整启用分类树
    @GetMapping("/category/tree")
    public Result<List<Category>> tree() {
        return Result.success(categoryService.tree());
    }

    @PostMapping("/category/add")
    public Result<Category> add(@RequestBody @Valid Category category) {
        requireLogin();
        return Result.success(categoryService.add(category));
    }

    @PutMapping("/category/update")
    public Result update(@RequestBody @Valid Category category) {
        requireLogin();
        categoryService.update(category);
        return Result.success();
    }

    private void requireLogin() {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            throw new BusinessException("请先登录");
        }
    }
}
