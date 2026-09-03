package com.inventory.exception;

/**
 * 库存锁定业务失败（商品未初始化库存 / 可用库存不足）。
 * 抛出后整单事务回滚（全有或全无），消费端据此回执 inventory.deduct_failed。
 */
public class StockLockException extends RuntimeException {

    public StockLockException(String message) {
        super(message);
    }
}
