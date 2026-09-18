package com.user.mq;

import com.model.event.OrderCanceledEvent;
import com.model.event.OrderRefundedEvent;
import com.user.config.UserRabbitConfig;
import com.user.service.CouponService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 退券消费者：订单取消 / 已退款 -> 把该订单用掉的券退回。
 *
 * **幂等**：退券是 `where order_id=? and status=1` 的条件 UPDATE，重投不会重复退，
 * 也不会碰别的订单的券。这一点是必须的——两个队列都可能对该订单触发，
 * 且事件本身可能被重投（relay 是 at-least-once）。
 *
 * 只认 {@code orderId}，不接受客户端传入的券 id——见
 * {@link com.user.mapper.UserCouponMapper#releaseByOrderId} 的说明。
 *
 * 消费失败由容器工厂的有界重试兜底（3 次尝试），耗尽后落 q.user.dlq。
 */
@Component
@Slf4j
public class CouponReleaseListener {

    @Autowired
    CouponService couponService;

    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_CANCELED)
    public void onOrderCanceled(OrderCanceledEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.canceled 事件缺少 orderId，跳过退券");
            return;
        }
        couponService.releaseByOrderId(event.getOrderId());
    }

    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_REFUNDED)
    public void onOrderRefunded(OrderRefundedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.refunded 事件缺少 orderId，跳过退券");
            return;
        }
        couponService.releaseByOrderId(event.getOrderId());
    }
}
