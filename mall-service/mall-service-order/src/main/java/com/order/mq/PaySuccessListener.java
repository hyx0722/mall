package com.order.mq;

import com.model.event.PaySuccessEvent;
import com.order.config.OrderRabbitConfig;
import com.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单服务消费支付成功回执（payment 侧 pay.success）：
 * 订单 待付款 -> 待发货（markPaid 带 order_status=0 条件，天然防重复翻转）。
 */
@Component
@Slf4j
public class PaySuccessListener {

    @Autowired
    OrderService orderService;

    @RabbitListener(queues = OrderRabbitConfig.Q_PAY_SUCCESS)
    public void onPaySuccess(PaySuccessEvent event) {
        log.info("[order] 收到支付成功回执 payNo={} orderId={}", event.getPayNo(), event.getOrderId());
        orderService.handlePaid(event);
    }
}
