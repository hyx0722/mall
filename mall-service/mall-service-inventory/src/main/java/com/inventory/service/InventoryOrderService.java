package com.inventory.service;

import java.util.Map;

/**
 * 库存消费侧（order.created / order.canceled）的本地事务封装：
 * 扣/释与流水在同一事务，配合 DB 流水幂等，消除「先 SETNX 后干活崩溃 → 订单悬挂」窗口。
 */
public interface InventoryOrderService {

    /**
     * 全有或全无地锁定一个订单的全部商品并写 change_type=3 流水。
     * 任一商品库存不足/未初始化抛 {@code StockLockException} -> 整单回滚（无部分锁定）。
     * 已有该订单流水（重复投递）则跳过该商品，天然幂等。
     */
    void lockForOrder(Long orderId, Map<Long, Integer> productQty);

    /**
     * 全有或全无地释放一个订单的全部锁定库存并写 change_type=4 流水。
     * 依赖 DB 条件更新 + 流水幂等，重复投递无副作用。
     */
    void releaseForOrder(Long orderId, Map<Long, Integer> productQty);
}
