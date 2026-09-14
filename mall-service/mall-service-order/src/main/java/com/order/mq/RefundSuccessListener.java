package com.order.mq;

import com.model.event.PayRefundSuccessEvent;
import com.order.config.OrderRabbitConfig;
import com.order.service.OrderRefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单服务消费退款到账回执（payment 侧 pay.refund.success）：
 * 订单 退款中 -> 已退款（markRefunded 带 order_status=5 条件，天然防重复推进），
 * 并在同一事务内登记 order.refunded 事件供 inventory 回补库存。
 */
@Component
@Slf4j
public class RefundSuccessListener {

    @Autowired
    OrderRefundService orderRefundService;

    @RabbitListener(queues = OrderRabbitConfig.Q_ORDER_REFUND_SUCCESS)
    public void onRefundSuccess(PayRefundSuccessEvent event) {
        log.info("[order] 收到退款到账回执 refundNo={} orderId={}", event.getRefundNo(), event.getOrderId());
        orderRefundService.handleRefundSuccess(event);
    }
}
