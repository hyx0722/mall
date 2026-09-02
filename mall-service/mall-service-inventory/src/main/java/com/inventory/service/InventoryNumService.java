package com.inventory.service;

import com.model.bean.Inventory;
import com.model.bean.Product;

import java.util.List;

public interface InventoryNumService {

    Inventory findNumInventory(Product product);

    void addNumInventory(Product product);

    /** 商家给自有商品补货（校验归属并写库存流水） */
    void restock(Long userId, Long productId, Integer qty);

    /** 管理员：查询库存（可按商品 id 过滤） */
    List<Inventory> listAllInventory(Long productId);
}
