package com.inventory.service;

import com.model.bean.Inventory;
import com.model.bean.Product;

import java.util.List;

public interface InventoryFeignService {

    Inventory findNumInventory(Product product);

    /** 为商品初始化库存（校验商品归属后写入，userId 以登录态为准） */
    void addNumInventory(Long userId, Product product);

    /** 商家给自有商品补货（校验归属并写库存流水） */
    void restock(Long userId, Long productId, Integer qty);

    /**
     * 商家设置自有商品的库存预警阈值（校验归属）。
     * {@code threshold} 为 0 表示不预警——这也是「关掉预警」的唯一方式。
     */
    void updateWarnThreshold(Long userId, Long productId, Integer threshold);

    /** 管理员：查询库存（可按商品 id 过滤） */
    List<Inventory> listAllInventory(Long productId);
}
