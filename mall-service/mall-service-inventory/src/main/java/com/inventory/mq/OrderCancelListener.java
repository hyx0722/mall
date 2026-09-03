package com.inventory.mq;

import com.inventory.config.InventoryRabbitConfig;
import com.inventory.service.InventoryOrderService;
import com.model.event.OrderCanceledEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消费 order.canceled：释放该订单下单时锁定的库存并写 change_type=4 流水。
 * 幂等由 DB 流水承担（InventoryOrderService.releaseForOrder：已有该订单 release 流水则跳过，
 * releaseLocked 为 locked_stock>=qty 条件更新），不再用 Redis SETNX。
 */
@Component
@Slf4j
public class OrderCancelListener {

    @Autowired
    InventoryOrderService inventoryOrderService;

    @RabbitListener(queues = InventoryRabbitConfig.Q_ORDER_CANCELED)
    public void onOrderCanceled(OrderCanceledEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }
        Map<Long, Integer> productQty = new LinkedHashMap<>();
        for (OrderCanceledEvent.Item item : event.getItems()) {
            productQty.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        inventoryOrderService.releaseForOrder(event.getOrderId(), productQty);
    }
}
