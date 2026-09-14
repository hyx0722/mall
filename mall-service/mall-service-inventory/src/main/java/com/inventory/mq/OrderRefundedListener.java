package com.inventory.mq;

import com.inventory.config.InventoryRabbitConfig;
import com.inventory.service.InventoryOrderService;
import com.model.event.OrderRefundedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消费 order.refunded：退款到账后回补该订单占用的库存，写 change_type=6（退货入库）流水。
 *
 * 幂等由 DB 流水承担：inventory_log 的 (order_id, product_id, change_type) 唯一键 +
 * returnForOrder 先查流水再释放，消息重投无副作用（与取消释放链路互不干扰，二者 type 不同）。
 */
@Component
@Slf4j
public class OrderRefundedListener {

    @Autowired
    InventoryOrderService inventoryOrderService;

    @RabbitListener(queues = InventoryRabbitConfig.Q_ORDER_REFUNDED)
    public void onOrderRefunded(OrderRefundedEvent event) {
        if (event == null || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }
        Map<Long, Integer> productQty = new LinkedHashMap<>();
        for (OrderRefundedEvent.Item item : event.getItems()) {
            productQty.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        log.info("[inventory] 订单 {} 退款到账，回补库存 {} 个商品", event.getOrderNo(), productQty.size());
        inventoryOrderService.returnForOrder(event.getOrderId(), productQty);
    }
}
