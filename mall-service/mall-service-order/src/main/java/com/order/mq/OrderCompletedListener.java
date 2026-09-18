package com.order.mq;

import com.model.event.OrderCompletedEvent;
import com.order.config.OrderRabbitConfig;
import com.order.service.SettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消费 order.completed 生成商家结算明细。
 *
 * 幂等由 settlement 的 uk_order_item 唯一键保证（见 {@link SettlementService}），
 * 消费失败走容器工厂的有界重试，耗尽后落 q.order.dlq。
 */
@Component
@Slf4j
public class OrderCompletedListener {

    @Autowired
    SettlementService settlementService;

    @RabbitListener(queues = OrderRabbitConfig.Q_ORDER_COMPLETED)
    public void onOrderCompleted(OrderCompletedEvent event) {
        settlementService.settleOrder(event);
    }
}
