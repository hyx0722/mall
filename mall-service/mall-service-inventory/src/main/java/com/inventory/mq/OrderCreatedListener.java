package com.inventory.mq;

import com.inventory.bean.InventoryLog;
import com.inventory.config.InventoryRabbitConfig;
import com.inventory.mapper.InventoryLogMapper;
import com.inventory.mapper.InventoryMapper;
import com.model.bean.Inventory;
import com.model.event.InventoryResultEvent;
import com.model.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 消费 order.created：对每个明细锁定库存（Redisson 分布式锁 + 条件 UPDATE），
 * 全部成功回执 inventory.deducted；任一失败则释放已锁定并回执 inventory.deduct_failed。
 */
@Component
@Slf4j
public class OrderCreatedListener {

    private static final int LOCK_WAIT_SECONDS = 5;

    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryLogMapper inventoryLogMapper;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = InventoryRabbitConfig.Q_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }
        log.info("[inventory] 收到下单事件 orderNo={}", event.getOrderNo());

        // 记录已锁定的 (productId, quantity)，失败时整体回补
        List<int[]> locked = new ArrayList<>();
        boolean ok = true;
        try {
            for (OrderCreatedEvent.Item item : event.getItems()) {
                RLock lock = redissonClient.getLock("lock:stock:" + item.getProductId());
                boolean gotLock = false;
                try {
                    gotLock = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
                    if (!gotLock) {
                        ok = false;
                        break;
                    }
                    if (!tryLockOne(event, item.getProductId(), item.getQuantity())) {
                        ok = false;
                        break;
                    }
                    locked.add(new int[]{item.getProductId(), item.getQuantity()});
                } finally {
                    if (gotLock && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ok = false;
        }

        if (ok) {
            InventoryResultEvent done = new InventoryResultEvent();
            done.setOrderNo(event.getOrderNo());
            rabbitTemplate.convertAndSend(InventoryRabbitConfig.ORDER_EXCHANGE,
                    InventoryRabbitConfig.RK_DEDUCTED, done);
            log.info("[inventory] 订单 {} 库存锁定完成", event.getOrderNo());
        } else {
            // 回补已锁定的库存，避免订单取消后占用
            for (int[] p : locked) {
                inventoryMapper.releaseLocked(p[0], p[1]);
            }
            InventoryResultEvent failed = new InventoryResultEvent();
            failed.setOrderNo(event.getOrderNo());
            rabbitTemplate.convertAndSend(InventoryRabbitConfig.ORDER_EXCHANGE,
                    InventoryRabbitConfig.RK_DEDUCT_FAILED, failed);
            log.warn("[inventory] 订单 {} 库存锁定失败", event.getOrderNo());
        }
    }

    /** 锁定单个商品库存并写流水；返回是否成功 */
    private boolean tryLockOne(OrderCreatedEvent event, Integer productId, Integer qty) {
        Inventory row = inventoryMapper.selectByProductId(productId);
        if (row == null) {
            return false; // 该商品未初始化库存
        }
        int affected = inventoryMapper.lockStock(productId, qty);
        if (affected == 0) {
            return false; // 可用库存不足
        }
        // 写库存流水（change_type=3 锁定）
        InventoryLog logRow = new InventoryLog();
        logRow.setProductId(productId);
        logRow.setOrderId(event.getOrderId());
        logRow.setChangeType(3);
        logRow.setChangeQuantity(qty);
        logRow.setBeforeTotalStock(row.getTotalStock());
        logRow.setAfterTotalStock(row.getTotalStock());
        logRow.setBeforeLockedStock(row.getLockedStock());
        logRow.setAfterLockedStock(row.getLockedStock() + qty);
        logRow.setRemark("下单锁定");
        inventoryLogMapper.insertLog(logRow);
        return true;
    }
}
