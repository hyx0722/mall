package com.payment.mq;

import com.model.event.OrderCanceledEvent;
import com.payment.config.PaymentRabbitConfig;
import com.payment.service.PayOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 支付服务消费订单取消事件（order.canceled）：
 * 订单支付超时被取消后，把该订单仍待支付的支付单置为关闭（payment_status=3），
 * 避免「订单已取消、支付单仍待支付」的台账不一致。条件更新天然幂等，重复投递无副作用。
 */
@Component
@Slf4j
public class OrderCancelListener {

    @Autowired
    PayOrderService payOrderService;

    @RabbitListener(queues = PaymentRabbitConfig.Q_ORDER_CANCELED)
    public void onOrderCanceled(OrderCanceledEvent event) {
        log.info("[pay] 收到订单取消事件 orderId={}", event.getOrderId());
        payOrderService.closeUnpaidByOrderId(event.getOrderId());
    }
}
