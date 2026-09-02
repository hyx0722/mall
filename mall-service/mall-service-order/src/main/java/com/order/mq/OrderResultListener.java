package com.order.mq;

import com.model.event.InventoryResultEvent;
import com.order.config.OrderRabbitConfig;
import com.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单服务消费库存扣减结果回执：
 *  - inventory.deducted      -> 订单 待付款 -> 待发货
 *  - inventory.deduct_failed -> 订单 待付款 -> 已取消
 */
@Component
@Slf4j
public class OrderResultListener {

    @Autowired
    OrderService orderService;

    @RabbitListener(queues = OrderRabbitConfig.Q_DEDUCTED)
    public void onDeducted(InventoryResultEvent event) {
        log.info("[order] 收到扣减成功回执 orderNo={}", event.getOrderNo());
        orderService.handleDeducted(event);
    }

    @RabbitListener(queues = OrderRabbitConfig.Q_DEDUCT_FAILED)
    public void onDeductFailed(InventoryResultEvent event) {
        log.info("[order] 收到扣减失败回执 orderNo={}", event.getOrderNo());
        orderService.handleDeductFailed(event);
    }
}
