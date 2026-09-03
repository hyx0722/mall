package com.inventory.mq;

import com.inventory.config.InventoryRabbitConfig;
import com.inventory.exception.StockLockException;
import com.inventory.service.InventoryOrderService;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消费 order.created：
 * 1) 按商品 id 升序获取 Redisson 分布式锁（跨订单一致加锁序，避免死锁）；
 * 2) 在单事务内完成「条件扣库存 + 写 change_type=3 流水」（InventoryOrderService），
 *    任一商品不足/未初始化 -> 整单回滚并回执 deduct_failed；全部成功回执 deducted；
 * 3) 幂等由 DB 流水承担（已有该订单 change_type=3 流水则跳过），不用「先 SETNX 后干活」，
 *    消除处理中崩溃 -> 重投被挡 -> 订单悬挂的窗口；重复投递重复回执，order 侧仅记日志幂等无害。
 */
@Component
@Slf4j
public class OrderCreatedListener {

    private static final int LOCK_WAIT_SECONDS = 5;

    @Autowired
    InventoryOrderService inventoryOrderService;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = InventoryRabbitConfig.Q_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }
        // 合并同商品行（防御性），保持 LinkedHashMap 遍历顺序稳定
        Map<Long, Integer> productQty = new LinkedHashMap<>();
        for (OrderCreatedEvent.Item item : event.getItems()) {
            productQty.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // 按商品 id 升序加锁
        List<Long> sortedIds = new ArrayList<>(productQty.keySet());
        Collections.sort(sortedIds);
        List<RLock> held = new ArrayList<>();
        boolean lockedAll = false;
        try {
            for (Long productId : sortedIds) {
                RLock lock = redissonClient.getLock("lock:stock:" + productId);
                boolean acquired;
                try {
                    acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[inventory] 订单 {} 获取商品 {} 锁被中断", event.getOrderNo(), productId);
                    acquired = false;
                }
                if (acquired) {
                    held.add(lock);
                } else {
                    log.warn("[inventory] 订单 {} 商品 {} 获取分布式锁超时", event.getOrderNo(), productId);
                    break;
                }
            }
            lockedAll = held.size() == sortedIds.size();
            if (!lockedAll) {
                replyDeductFailed(event);
                return;
            }
            try {
                inventoryOrderService.lockForOrder(event.getOrderId(), productQty);
            } catch (StockLockException e) {
                log.warn("[inventory] 订单 {} 库存锁定失败：{}", event.getOrderNo(), e.getMessage());
                replyDeductFailed(event);
                return;
            }
            replyDeducted(event);
        } finally {
            for (RLock lock : held) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    private void replyDeducted(OrderCreatedEvent event) {
        InventoryResultEvent done = new InventoryResultEvent();
        done.setOrderNo(event.getOrderNo());
        rabbitTemplate.convertAndSend(InventoryRabbitConfig.ORDER_EXCHANGE,
                InventoryRabbitConfig.RK_DEDUCTED, done);
        log.info("[inventory] 订单 {} 库存锁定完成", event.getOrderNo());
    }

    private void replyDeductFailed(OrderCreatedEvent event) {
        InventoryResultEvent failed = new InventoryResultEvent();
        failed.setOrderNo(event.getOrderNo());
        rabbitTemplate.convertAndSend(InventoryRabbitConfig.ORDER_EXCHANGE,
                InventoryRabbitConfig.RK_DEDUCT_FAILED, failed);
        log.warn("[inventory] 订单 {} 库存锁定失败，回执 deduct_failed", event.getOrderNo());
    }
}
