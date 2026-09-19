package com.user.mq;

import com.model.event.OrderCompletedEvent;
import com.model.event.OrderCreatedEvent;
import com.model.event.OrderShippedEvent;
import com.model.event.PaySuccessEvent;
import com.user.bean.Notification;
import com.user.config.UserRabbitConfig;
import com.user.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 站内通知消费者：订单链路的 4 个事件 -> 给买家写一条通知。
 *
 * <p><b>为什么只有 4 个方法，「订单取消」「退款到账」不在这里？</b>
 * 那两条事件（{@code order.canceled} / {@code order.refunded}）已经在
 * {@link CouponReleaseListener} 里被消费了。给同一条队列再加一个 {@code @RabbitListener}
 * 会产生**两个竞争消费者**：Spring AMQP 轮询投递，退券和写通知各拿到约一半消息，
 * 且不报错、不记日志、不进 DLQ。所以那两条通知写在 {@link CouponReleaseListener}
 * 现有的方法体里，本类只接管 4 条新队列。
 *
 * <p><b>幂等</b>：本类**完全不处理重复**——那是 {@code NotificationService.record} 的职责
 * （撞 {@code uk_user_type_ref} 后捕获 {@code DuplicateKeyException} 静默返回）。
 * 监听方法里再包一层 try/catch 只会掩盖真正的故障，别加。
 *
 * <p><b>幂等键的选择</b>：一律用 {@code orderId}（而不是 {@code payNo} 之类的流水号）+
 * 各不相同的 {@code type}。这样一张订单的 4 条通知互不冲突，
 * 而同一事件重投时恰好撞上完全相同的键。
 */
@Component
@Slf4j
public class NotificationListener {

    @Autowired
    NotificationService notificationService;

    /** 下单成功 */
    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.created 事件缺少 orderId，跳过通知");
            return;
        }
        notificationService.record(
                event.getUserId(),
                Notification.TYPE_ORDER_CREATED,
                "订单提交成功",
                "订单 " + event.getOrderNo() + " 已提交，请尽快完成支付。",
                Notification.REF_ORDER,
                event.getOrderId(),
                Notification.STORE_NONE);
    }

    /**
     * 支付成功。
     *
     * 幂等键用 {@code orderId} 而**不是** {@code payNo}：通知是「这张订单已付款」，
     * 一张订单只该有一条支付通知。同一订单若出现两条支付记录（重复回调等），
     * 用 payNo 会写出两条内容几乎相同的通知。
     */
    @RabbitListener(queues = UserRabbitConfig.Q_USER_PAY_SUCCESS)
    public void onPaySuccess(PaySuccessEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] pay.success 事件缺少 orderId，跳过通知");
            return;
        }
        notificationService.record(
                event.getUserId(),
                Notification.TYPE_PAID,
                "支付成功",
                "订单已支付，商家将尽快为你发货。",
                Notification.REF_ORDER,
                event.getOrderId(),
                Notification.STORE_NONE);
    }

    /**
     * 订单已发货。
     *
     * 事件只在**整单**发完时发布一次（多卖家订单里是最后一个卖家的动作），
     * 所以这里天然每张订单只写一条——若哪天改成每个卖家发一次，
     * 第二条会被幂等键静默吞掉，是丢失不是重复。详见 OrderShippedEvent 的类注释。
     *
     * 运单号两个字段都可空，文案要容忍。
     */
    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_SHIPPED)
    public void onOrderShipped(OrderShippedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.shipped 事件缺少 orderId，跳过通知");
            return;
        }
        String detail = trackingText(event.getLogisticsCompany(), event.getTrackingNo());
        notificationService.record(
                event.getUserId(),
                Notification.TYPE_SHIPPED,
                "订单已发货",
                "你的订单已发货" + detail + "，点击查看物流详情。",
                Notification.REF_ORDER,
                event.getOrderId(),
                Notification.STORE_NONE);
    }

    /** 订单已完成 */
    @RabbitListener(queues = UserRabbitConfig.Q_USER_ORDER_COMPLETED)
    public void onOrderCompleted(OrderCompletedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[user] order.completed 事件缺少 orderId，跳过通知");
            return;
        }
        notificationService.record(
                event.getUserId(),
                Notification.TYPE_COMPLETED,
                "订单已完成",
                "订单 " + event.getOrderNo() + " 已确认收货，感谢你的购买。",
                Notification.REF_ORDER,
                event.getOrderId(),
                Notification.STORE_NONE);
    }

    /** 拼「（顺丰：SF123）」。两个字段都可空，缺一个就少拼一段，都缺就返回空串 */
    static String trackingText(String company, String trackingNo) {
        boolean hasCompany = company != null && !company.isBlank();
        boolean hasNo = trackingNo != null && !trackingNo.isBlank();
        if (!hasCompany && !hasNo) {
            return "";
        }
        if (hasCompany && hasNo) {
            return "（" + company + "：" + trackingNo + "）";
        }
        return "（" + (hasCompany ? company : trackingNo) + "）";
    }
}
