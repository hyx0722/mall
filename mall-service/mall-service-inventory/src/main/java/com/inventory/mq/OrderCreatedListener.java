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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 消费 order.created：对每个明细锁定库存（Redisson 分布式锁 + 条件 UPDATE），
 * 全部成功回执 inventory.deducted；任一失败则释放已锁定并回执 inventory.deduct_failed。
 *
 * 幂等：入口用 Redis SETNX 对 orderNo 打标（TTL 24h），重复投递/重投直接跳过，防止同一订单被扣两次库存。
 */
@Component
@Slf4j
public class OrderCreatedListener {

    private static final int LOCK_WAIT_SECONDS = 5;
    private static final long DEDUP_TTL_HOURS = 24L;

    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryLogMapper inventoryLogMapper;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = InventoryRabbitConfig.Q_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }

        // 幂等：同 orderNo 只处理一次（at-least-once 下防重复扣减）
        String dedupKey = "dedup:order:" + event.getOrderNo();
        Boolean firstTime = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("[inventory] 订单 {} 已处理过(重复消息)，跳过", event.getOrderNo());
            return;
        }
        log.info("[inventory] 收到下单事件 orderNo={}", event.getOrderNo());

        // 记录已锁定的 (productId -> qty)，失败时整体回补
        Map<Long, Integer> locked = new LinkedHashMap<>();
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
                    locked.put(item.getProductId(), item.getQuantity());
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
            for (Map.Entry<Long, Integer> p : locked.entrySet()) {
                inventoryMapper.releaseLocked(p.getKey(), p.getValue());
            }
            InventoryResultEvent failed = new InventoryResultEvent();
            failed.setOrderNo(event.getOrderNo());
            rabbitTemplate.convertAndSend(InventoryRabbitConfig.ORDER_EXCHANGE,
                    InventoryRabbitConfig.RK_DEDUCT_FAILED, failed);
            log.warn("[inventory] 订单 {} 库存锁定失败", event.getOrderNo());
        }
    }

    /** 锁定单个商品库存并写流水；返回是否成功 */
    private boolean tryLockOne(OrderCreatedEvent event, Long productId, Integer qty) {
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
