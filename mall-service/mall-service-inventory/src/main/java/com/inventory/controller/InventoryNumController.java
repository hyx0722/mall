package com.inventory.controller;

import com.inventory.service.InventoryNumService;
import com.model.bean.Product;
import com.model.bean.Result;
import com.model.util.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Slf4j
@Validated
public class InventoryNumController {

    @Autowired
    InventoryNumService inventoryNumService;

    //为商品初始化库存：product_id 唯一键防重，重复初始化解 DuplicateKey 时显式报错
    @PostMapping("/addNumInventory")
    public Result addNumInventory(@RequestBody @Validated Product product){
        if(inventoryNumService.findNumInventory(product)!=null){
            return Result.error("该商品已存在库存中，请修改");
        }
        try {
            inventoryNumService.addNumInventory(product);
        } catch (DuplicateKeyException e) {
            return Result.error("该商品已存在库存中，请修改");
        }
        return Result.success();
    }

    //商家给自有商品补货（归属以登录态 user_id 为准）
    @PostMapping("/restock")
    public Result restock(@RequestParam Integer productId, @RequestParam Integer qty) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (map == null) ? null : (Integer) map.get("id");
        inventoryNumService.restock(userId, productId, qty);
        return Result.success();
    }
}
