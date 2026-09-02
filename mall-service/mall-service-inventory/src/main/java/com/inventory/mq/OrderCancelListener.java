package com.inventory.mq;

import com.inventory.bean.InventoryLog;
import com.inventory.config.InventoryRabbitConfig;
import com.inventory.mapper.InventoryLogMapper;
import com.inventory.mapper.InventoryMapper;
import com.model.bean.Inventory;
import com.model.event.OrderCanceledEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消费 order.canceled（支付超时自动取消）：
 * 释放该订单下单时锁定的库存并写 change_type=4 释放流水。
 *
 * 幂等：SETNX 对 orderNo 打标（TTL 24h）防重复释放；releaseLocked 为
 * locked_stock>=qty 条件更新，重放亦无副作用。cancel_time 语义与 OrderCreatedListener 对齐。
 */
@Component
@Slf4j
public class OrderCancelListener {

    private static final long DEDUP_TTL_HOURS = 24L;

    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryLogMapper inventoryLogMapper;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = InventoryRabbitConfig.Q_ORDER_CANCELED)
    public void onOrderCanceled(OrderCanceledEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }
        String dedupKey = "dedup:order.cancel:" + event.getOrderNo();
        Boolean firstTime = stringRedisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(firstTime)) {
            log.info("[inventory] 订单 {} 取消已处理过(重复消息)，跳过", event.getOrderNo());
            return;
        }
        log.info("[inventory] 收到订单取消事件 orderNo={}", event.getOrderNo());

        for (OrderCanceledEvent.Item item : event.getItems()) {
            releaseLocked(event, item.getProductId(), item.getQuantity());
        }
    }

    /** 释放单个商品锁定并写流水；条件更新失败（如已释放/锁定不足）则跳过 */
    private void releaseLocked(OrderCanceledEvent event, Long productId, Integer qty) {
        Inventory row = inventoryMapper.selectByProductId(productId);
        if (row == null) {
            return;
        }
        int affected = inventoryMapper.releaseLocked(productId, qty);
        if (affected == 0) {
            log.warn("[inventory] 订单 {} 商品 {} 释放锁定失败(可能已释放)", event.getOrderNo(), productId);
            return;
        }
        // 写库存流水（change_type=4 释放锁定）
        InventoryLog logRow = new InventoryLog();
        logRow.setProductId(productId);
        logRow.setOrderId(event.getOrderId());
        logRow.setChangeType(4);
        logRow.setChangeQuantity(qty);
        logRow.setBeforeTotalStock(row.getTotalStock());
        logRow.setAfterTotalStock(row.getTotalStock());
        logRow.setBeforeLockedStock(row.getLockedStock());
        logRow.setAfterLockedStock(row.getLockedStock() - qty);
        logRow.setRemark("支付超时释放");
        inventoryLogMapper.insertLog(logRow);
    }
}
