package com.inventory.mq;

import com.inventory.config.InventoryRabbitConfig;
import com.inventory.exception.StockLockException;
import com.inventory.service.InventoryOrderService;
import com.mall.common.outbox.OutboxService;
import com.model.event.InventoryResultEvent;
import com.model.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
 * 2) 在单事务内完成「条件扣库存 + 写 change_type=3 流水 + deducted 回执入箱」
 *    （InventoryOrderService），任一商品不足/未初始化 -> 整单回滚；
 * 3) 幂等由 DB 流水承担（已有该订单 change_type=3 流水则跳过），不用「先 SETNX 后干活」，
 *    消除处理中崩溃 -> 重投被挡 -> 订单悬挂的窗口；重复投递重复回执，order 侧仅记日志幂等无害。
 *
 * 回执一律经 outbox 投递，本类**不再直接发 MQ**：
 * - 成功回执由 lockForOrder 在事务内入箱（与库存锁定同生共死）；
 * - 失败回执产生于该事务回滚之后，必须用独立事务入箱（enqueueNewTx）——
 *   否则它会挂在那个即将回滚的事务里被一并撤销，订单永远收不到 deduct_failed。
 *   而丢失败回执不只是「晚点取消」：库存没锁上，订单侧 createPayOrder 只校验订单状态
 *   不校验库存，买家在超时前仍可支付，订单会带着零库存推进到待发货（超卖）。
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
    OutboxService outboxService;

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
                inventoryOrderService.lockForOrder(event.getOrderId(), event.getOrderNo(), productQty);
            } catch (StockLockException e) {
                log.warn("[inventory] 订单 {} 库存锁定失败：{}", event.getOrderNo(), e.getMessage());
                replyDeductFailed(event);
                return;
            }
        } finally {
            for (RLock lock : held) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * 失败回执入箱。此处 lockForOrder 的事务**已经回滚**（或压根没开启），
     * 故必须用独立事务（enqueueNewTx）提交，否则回执会随回滚一起消失。
     * 入箱本身失败会抛出 -> 交给监听容器的有界重试，重试耗尽落 q.inventory.dlq 等人工介入，
     * 不会静默丢失。
     */
    private void replyDeductFailed(OrderCreatedEvent event) {
        InventoryResultEvent failed = new InventoryResultEvent();
        failed.setOrderNo(event.getOrderNo());
        outboxService.enqueueNewTx(InventoryRabbitConfig.ORDER_EXCHANGE,
                InventoryRabbitConfig.RK_DEDUCT_FAILED, null, failed);
        log.warn("[inventory] 订单 {} 库存锁定失败，回执 deduct_failed 已入箱", event.getOrderNo());
    }
}
