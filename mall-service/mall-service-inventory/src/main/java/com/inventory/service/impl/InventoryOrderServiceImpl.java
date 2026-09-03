package com.inventory.service.impl;

import com.inventory.bean.InventoryLog;
import com.inventory.exception.StockLockException;
import com.inventory.mapper.InventoryLogMapper;
import com.inventory.mapper.InventoryMapper;
import com.inventory.service.InventoryOrderService;
import com.model.bean.Inventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 库存消费侧本地事务：扣/释库存与写流水同事务。
 * 以 inventory_log（order_id, product_id, change_type）为幂等真相：先查流水，已有则跳过，
 * 替换原先「Redis SETNX 先打标」的方案，消除「打标后进程崩溃 → 重投被挡 → 订单悬挂」的窗口。
 */
@Service
@Slf4j
public class InventoryOrderServiceImpl implements InventoryOrderService {

    private static final int CHANGE_LOCK = 3;
    private static final int CHANGE_RELEASE = 4;

    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryLogMapper inventoryLogMapper;

    @Override
    @Transactional
    public void lockForOrder(Long orderId, Map<Long, Integer> productQty) {
        for (Map.Entry<Long, Integer> e : productQty.entrySet()) {
            Long productId = e.getKey();
            Integer qty = e.getValue();
            if (qty == null || qty <= 0) {
                continue;
            }
            // 幂等：该订单该商品已有 change_type=3 锁定流水 -> 已锁过（重投），跳过
            if (inventoryLogMapper.countByOrderAndProductType(orderId, productId, CHANGE_LOCK) > 0) {
                log.info("[inventory] 订单 {} 商品 {} 已有锁定流水，跳过重复锁定", orderId, productId);
                continue;
            }
            Inventory row = inventoryMapper.selectByProductId(productId);
            if (row == null) {
                throw new StockLockException("商品库存未初始化: productId=" + productId);
            }
            int affected = inventoryMapper.lockStock(productId, qty);
            if (affected == 0) {
                throw new StockLockException("可用库存不足: productId=" + productId);
            }
            InventoryLog logRow = new InventoryLog();
            logRow.setProductId(productId);
            logRow.setOrderId(orderId);
            logRow.setChangeType(CHANGE_LOCK);
            logRow.setChangeQuantity(qty);
            logRow.setBeforeTotalStock(row.getTotalStock());
            logRow.setAfterTotalStock(row.getTotalStock());
            logRow.setBeforeLockedStock(row.getLockedStock());
            logRow.setAfterLockedStock(row.getLockedStock() + qty);
            logRow.setRemark("下单锁定");
            inventoryLogMapper.insertLog(logRow);
        }
    }

    @Override
    @Transactional
    public void releaseForOrder(Long orderId, Map<Long, Integer> productQty) {
        for (Map.Entry<Long, Integer> e : productQty.entrySet()) {
            Long productId = e.getKey();
            Integer qty = e.getValue();
            if (qty == null || qty <= 0) {
                continue;
            }
            // 幂等：已释放过（有 change_type=4 流水）则跳过
            if (inventoryLogMapper.countByOrderAndProductType(orderId, productId, CHANGE_RELEASE) > 0) {
                continue;
            }
            Inventory row = inventoryMapper.selectByProductId(productId);
            if (row == null) {
                log.warn("[inventory] 订单 {} 商品 {} 无库存记录，跳过释放", orderId, productId);
                continue;
            }
            int affected = inventoryMapper.releaseLocked(productId, qty);
            if (affected == 0) {
                log.warn("[inventory] 订单 {} 商品 {} 释放锁定失败(可能已释放)，跳过", orderId, productId);
                continue;
            }
            InventoryLog logRow = new InventoryLog();
            logRow.setProductId(productId);
            logRow.setOrderId(orderId);
            logRow.setChangeType(CHANGE_RELEASE);
            logRow.setChangeQuantity(qty);
            logRow.setBeforeTotalStock(row.getTotalStock());
            logRow.setAfterTotalStock(row.getTotalStock());
            logRow.setBeforeLockedStock(row.getLockedStock());
            logRow.setAfterLockedStock(row.getLockedStock() - qty);
            logRow.setRemark("订单取消释放");
            inventoryLogMapper.insertLog(logRow);
        }
    }
}
