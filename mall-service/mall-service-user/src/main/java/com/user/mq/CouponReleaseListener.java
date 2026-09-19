package com.user.mq;

import com.model.event.OrderCanceledEvent;
import com.model.event.OrderRefundedEvent;
import com.user.bean.Notification;
import com.user.config.UserRabbitConfig;
import com.user.service.CouponService;
import com.user.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 退券消费者：订单取消 / 已退款 -> 把该订单用掉的券退回，**并顺带写一条站内通知**。
 *
 * <p>⚠️ <b>这两个方法各承担两件事（退券 + 写通知），是刻意的。绝不要为通知另加一个
 * {@code @RabbitListener} 注解到同一条队列上——那是两个竞争消费者，Spring AMQP 会轮询投递，
 * 退券和写通知各拿到约一半消息，且不报错、不记日志、不进 DLQ，只表现为两张功能都时灵时不灵。</b>
 * 站内通知的另外 4 条队列在 {@link NotificationListener} 里，与这里没有重叠。
 *
 * **幂等**：退券是 `where order_id=? and status=1` 的条件 UPDATE，重投不会重复退，
 * 也不会碰别的订单的券。写通知则由 {@code uk_user_type_ref} 唯一键去重
 * （{@code NotificationService.record} 内部吞掉 DuplicateKeyException）。
 * 两点都是必须的——两个队列都可能对该订单触发，且事件本身可能被重投（relay 是 at-least-once）。
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
    @Autowired
    NotificationService notificationService;

    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_CANCELED)
    public void onOrderCanceled(OrderCanceledEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.canceled 事件缺少 orderId，跳过退券");
            return;
        }
        couponService.releaseByOrderId(event.getOrderId());
        // 同一方法内顺带写通知——不要另开 @RabbitListener，理由见类注释
        notificationService.record(
                event.getUserId(),
                Notification.TYPE_CANCELED,
                "订单已取消",
                "订单 " + event.getOrderNo() + " 已取消。若已用券，券已退回你的背包。",
                Notification.REF_ORDER,
                event.getOrderId(),
                Notification.STORE_NONE);
    }

    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_REFUNDED)
    public void onOrderRefunded(OrderRefundedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.refunded 事件缺少 orderId，跳过退券");
            return;
        }
        couponService.releaseByOrderId(event.getOrderId());
        notificationService.record(
                event.getUserId(),
                Notification.TYPE_REFUNDED,
                "退款已到账",
                "订单 " + event.getOrderNo() + " 的退款已原路退回，请留意账户到账通知。",
                Notification.REF_ORDER,
                event.getOrderId(),
                Notification.STORE_NONE);
    }
}
