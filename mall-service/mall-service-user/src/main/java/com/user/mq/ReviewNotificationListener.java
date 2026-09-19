package com.user.mq;

import com.model.event.ReviewCreatedEvent;
import com.model.event.ReviewRepliedEvent;
import com.user.bean.Notification;
import com.user.config.UserRabbitConfig;
import com.user.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 评价相关的站内通知：{@code review.created} / {@code review.replied}。
 *
 * <p>两条队列各自独占一个监听方法。⚠️ <b>绝不能给同一条队列再加一个 {@code @RabbitListener}</b>——
 * 那是两个竞争消费者，Spring AMQP 轮询投递，两个方法各拿到约一半消息，
 * 且不报错、不记日志、不进 DLQ。既有的 6 条通知队列在 {@link NotificationListener}
 * 与 {@link CouponReleaseListener}，与本类没有重叠。
 *
 * <p>⚠️ <b>两个方法都不得加 {@code @Transactional}</b>：{@code NotificationService.record}
 * 靠捕获 {@code DuplicateKeyException} 实现幂等，而这在事务里会把事务标成 rollback-only。
 *
 * <p>幂等由 {@code notification.uk_user_type_ref(user_id, type, ref_id)} 保证，
 * <b>两种类型的 ref_id 都是 reviewId</b>——用 productId 会让商家对同一商品只收到第一条评价通知，
 * 用 orderId 会让同一订单里多条评价的回复互相顶掉。详见 {@link Notification#REF_REVIEW}。
 */
@Component
@Slf4j
public class ReviewNotificationListener {

    /**
     * 用户输入的文本在通知里的截断长度。
     *
     * <p>⚠️ 这个截断**不能省**：{@code notification.content} 是 {@code VARCHAR(500)}，
     * 而评价正文本身的上限就是 500 字。把整条评价拼上前缀写进去，严格 SQL 模式会抛异常 →
     * 重试耗尽 → 落 {@code q.user.dlq}，把真正的毒消息埋在同一堆里；非严格模式则静默截断。
     */
    private static final int CLIP_EXCERPT = 80;
    private static final int CLIP_PRODUCT = 30;
    private static final int CLIP_USERNAME = 20;

    @Autowired
    NotificationService notificationService;

    /** 买家写了评价 -> 通知**卖家** */
    @RabbitListener(queues = UserRabbitConfig.Q_USER_REVIEW_CREATED)
    public void onReviewCreated(ReviewCreatedEvent event) {
        if (event == null || event.getReviewId() == null) {
            log.warn("[user] review.created 事件缺少 reviewId，跳过通知");
            return;
        }
        if (event.getSellerId() == null) {
            log.warn("[user] review.created 事件缺少 sellerId，跳过通知 reviewId={}", event.getReviewId());
            return;
        }
        // title 是固定短语、不含用户输入，不会被撑爆
        String content = clip(event.getBuyerName(), CLIP_USERNAME)
                + " 给「" + clip(event.getProductName(), CLIP_PRODUCT) + "」打了 "
                + event.getRating() + " 星："
                + clip(event.getContent(), CLIP_EXCERPT);
        notificationService.record(
                event.getSellerId(),
                Notification.TYPE_REVIEW_CREATED,
                "你的商品收到新评价",
                content,
                Notification.REF_REVIEW,
                event.getReviewId(),
                // store_id = 0：收件人就是店主本人，填自己的 id 会显示成「来自店铺：我自己」
                Notification.STORE_NONE);
    }

    /** 卖家回复了评价 -> 通知**买家** */
    @RabbitListener(queues = UserRabbitConfig.Q_USER_REVIEW_REPLIED)
    public void onReviewReplied(ReviewRepliedEvent event) {
        if (event == null || event.getReviewId() == null) {
            log.warn("[user] review.replied 事件缺少 reviewId，跳过通知");
            return;
        }
        if (event.getBuyerId() == null) {
            log.warn("[user] review.replied 事件缺少 buyerId，跳过通知 reviewId={}", event.getReviewId());
            return;
        }
        String content = "你给「" + clip(event.getProductName(), CLIP_PRODUCT) + "」的评价收到了商家回复："
                + clip(event.getReplyContent(), CLIP_EXCERPT);
        notificationService.record(
                event.getBuyerId(),
                Notification.TYPE_REVIEW_REPLIED,
                "商家回复了你的评价",
                content,
                Notification.REF_REVIEW,
                event.getReviewId(),
                // store_id = 卖家：买家由此看到「来自店铺：xxx」，知道是哪家店回复的。
                // 与上面类型 10 的 STORE_NONE 不对称是**刻意的**——那边收件人就是店主。
                event.getSellerId());
    }

    /** 截断到 max 个字符（超长时补省略号）。空/null 归一成空串，避免拼出「null」 */
    static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
