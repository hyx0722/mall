package com.order.mq;

import com.model.event.OrderTimeoutEvent;
import com.order.config.OrderRabbitConfig;
import com.order.service.OrderCancelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消费支付超时延迟标记（下单时经 mall.order.delay.exchange + per-message TTL 死信回主交换机
 * order.timeout 到达）：走统一取消漏斗，仅在订单仍待付款时取消并登记 order.canceled。
 */
@Component
@Slf4j
public class OrderTimeoutListener {

    @Autowired
    OrderCancelService orderCancelService;

    @RabbitListener(queues = OrderRabbitConfig.Q_ORDER_TIMEOUT)
    public void onOrderTimeout(OrderTimeoutEvent event) {
        log.info("[order] 收到支付超时标记 orderNo={}", event.getOrderNo());
        orderCancelService.cancelByOrderNo(event.getOrderNo());
    }
}
