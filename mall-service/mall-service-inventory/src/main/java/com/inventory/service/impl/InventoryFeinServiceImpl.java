package com.inventory.service.impl;

import com.inventory.bean.InventoryLog;
import com.inventory.mapper.InventoryLogMapper;
import com.inventory.mapper.InventoryFeinMapper;
import com.inventory.service.InventoryFeinService;
import com.model.bean.Inventory;
import com.model.bean.Product;
import com.model.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryFeinServiceImpl implements InventoryFeinService {
    @Autowired
    InventoryFeinMapper inventoryFeinMapper;
    @Autowired
    InventoryLogMapper inventoryLogMapper;

    @Override
    public Inventory findNumInventory(Product product) {
        return inventoryFeinMapper.findNumInventory(product);
    }

    @Override
    @Transactional
    public void addNumInventory(Product product) {
        inventoryFeinMapper.addNumInventory(product);
    }

    @Override
    public java.util.List<Inventory> listAllInventory(Long productId) {
        return inventoryFeinMapper.findAllInventory(productId);
    }

    @Override
    @Transactional
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
        Inventory row = inventoryFeinMapper.findByProductIdAndUser(productId, userId);
        if (row == null) {
            throw new BusinessException("库存不存在或无权操作");
        }
        int affected = inventoryFeinMapper.addStock(productId, userId, qty);
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
