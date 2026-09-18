package com.inventory.service.impl;

import com.inventory.bean.InventoryLog;
import com.inventory.mapper.InventoryLogMapper;
import com.inventory.mapper.InventoryFeignMapper;
import com.inventory.service.InventoryFeignService;
import com.model.bean.Inventory;
import com.model.bean.Product;
import com.model.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryFeignServiceImpl implements InventoryFeignService {
    @Autowired
    InventoryFeignMapper inventoryFeignMapper;
    @Autowired
    InventoryLogMapper inventoryLogMapper;

    @Override
    public Inventory findNumInventory(Product product) {
        return inventoryFeignMapper.findNumInventory(product);
    }

    @Override
    @Transactional
    public void addNumInventory(Long userId, Product product) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (product.getId() == null) {
            throw new BusinessException("缺少商品 id");
        }
        // 归属校验：只能为自己名下的商品初始化库存（跨库读 product）
        if (inventoryFeignMapper.countOwnedProduct(product.getId(), userId) == 0) {
            throw new BusinessException("商品不存在或无权操作");
        }
        product.setUserId(userId);   // 归属以登录态为准，覆盖请求体
        inventoryFeignMapper.addNumInventory(product);
    }

    @Override
    public java.util.List<Inventory> listAllInventory(Long productId) {
        return inventoryFeignMapper.findAllInventory(productId);
    }

    @Override
    @Transactional
    public void updateWarnThreshold(Long userId, Long productId, Integer threshold) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        if (threshold == null || threshold < 0) {
            throw new BusinessException("预警阈值不能为负（填 0 表示关闭预警）");
        }
        // 条件 UPDATE 带 user_id：改不动别人商品的阈值，也无需先查再判断
        if (inventoryFeignMapper.updateWarnThreshold(productId, userId, threshold) == 0) {
            throw new BusinessException("库存不存在或无权操作");
        }
    }

    @Override
    public void restock(Long userId, Long productId, Integer qty) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        if (qty == null || qty < 1) {
            throw new BusinessException("补货数量必须大于 0");
        }
        Inventory row = inventoryFeignMapper.findByProductIdAndUser(productId, userId);
        if (row == null) {
            throw new BusinessException("库存不存在或无权操作");
        }
        int affected = inventoryFeignMapper.addStock(productId, userId, qty);
        if (affected == 0) {
            throw new BusinessException("补货失败，请重试");
        }
        // 库存流水：change_type=1 入库
        InventoryLog log = new InventoryLog();
        log.setProductId(productId);
        log.setChangeType(1);
        log.setChangeQuantity(qty);
        log.setBeforeTotalStock(row.getTotalStock());
        log.setAfterTotalStock(row.getTotalStock() + qty);
        log.setBeforeLockedStock(row.getLockedStock());
        log.setAfterLockedStock(row.getLockedStock());
        log.setRemark("商家补货");
        inventoryLogMapper.insertLog(log);
    }
}
