package com.inventory.controller;

import com.inventory.service.InventoryFeignService;
import com.mall.common.web.Auths;
import com.model.bean.Inventory;
import com.model.bean.Product;
import com.model.bean.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@Validated
public class InventoryFeignController {

    @Autowired
    InventoryFeignService inventoryFeignService;

    //为商品初始化库存：product_id 唯一键防重，重复初始化解 DuplicateKey 时显式报错。
    //归属只认登录态：service 会校验该商品确属调用者，请求体里的 userId 被覆盖。
    @PostMapping("/addNumInventory")
    public Result addNumInventory(@RequestBody @Validated Product product){
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        if(inventoryFeignService.findNumInventory(product)!=null){
            return Result.error("该商品已存在库存中，请修改");
        }
        try {
            inventoryFeignService.addNumInventory(userId, product);
        } catch (DuplicateKeyException e) {
            return Result.error("该商品已存在库存中，请修改");
        }
        return Result.success();
    }

    //商家给自有商品补货（归属以登录态 user_id 为准）
    @PostMapping("/restock")
    public Result restock(@RequestParam Long productId, @RequestParam Integer qty) {
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        inventoryFeignService.restock(userId, productId, qty);
        return Result.success();
    }

    //管理员：查询库存（可按商品 id 过滤）。经网关 /inventory/admin/listAll
    @GetMapping("/admin/listAll")
    public Result<List<Inventory>> listAll(@RequestParam(required = false) Long productId) {
        Auths.requireAdmin();
        return Result.success(inventoryFeignService.listAllInventory(productId));
    }
}
