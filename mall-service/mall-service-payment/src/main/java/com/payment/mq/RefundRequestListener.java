package com.payment.mq;

import com.model.event.RefundRequestEvent;
import com.payment.config.PaymentRabbitConfig;
import com.payment.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 支付服务消费退款指令（order 侧 refund.request）：
 * 申请建单 / 审核通过打款 / 审核驳回关闭，三种动作由事件体 action 区分。
 */
@Component
@Slf4j
public class RefundRequestListener {

    @Autowired
    RefundService refundService;

    @RabbitListener(queues = PaymentRabbitConfig.Q_REFUND_REQUEST)
    public void onRefundRequest(RefundRequestEvent event) {
        log.info("[pay] 收到退款指令 action={} refundNo={} orderId={}",
                event.getAction(), event.getRefundNo(), event.getOrderId());
        refundService.handleRequest(event);
    }
}
