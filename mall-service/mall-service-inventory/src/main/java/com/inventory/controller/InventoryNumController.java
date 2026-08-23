package com.inventory.controller;

import com.inventory.service.InventoryNumService;
import com.model.bean.Product;
import com.model.bean.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@Validated
public class InventoryNumController {

    @Autowired
    InventoryNumService inventoryNumService;

    @Transactional
    @PostMapping("addNumInventory")
    public Result addNumInventory(@RequestBody @Validated Product product){
        if(inventoryNumService.findNumInventory(product)!=null){
            return Result.error("该商品已存在库存中，请修改");
        }
        inventoryNumService.addNumInventory(product);
        return Result.success();
    }
}
