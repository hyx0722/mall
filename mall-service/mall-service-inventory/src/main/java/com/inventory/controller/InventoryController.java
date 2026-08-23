package com.inventory.controller;

import com.inventory.service.InventoryService;
import com.model.bean.Inventory;
import com.model.bean.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Slf4j
public class InventoryController {
    @Autowired
    InventoryService inventoryService;

    @PostMapping("/updateInventory")
    public Result updateInventory(@RequestBody @Validated Inventory inventory){
        inventoryService.updateInventory(inventory);
        return Result.success();
    }
}
