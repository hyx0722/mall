package com.inventory.service;

import com.model.bean.Inventory;
import com.model.bean.Product;

public interface InventoryNumService {

    Inventory findNumInventory(Product product);

    void addNumInventory(Product product);

    /** 商家给自有商品补货（校验归属并写库存流水） */
    void restock(Integer userId, Integer productId, Integer qty);
}
