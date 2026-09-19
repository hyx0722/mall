package com.model.event;

import lombok.Data;

/**
 * 商家已回复商品评价事件（order 服务 -> RabbitMQ -> user 服务写站内通知）。
 *
 * 卖家回复了某条评价、与回复的 UPDATE **同一本地事务**写入 outbox 后投递，收件人是评价人（买家）。
 *
 * ── 消费侧的幂等键 ────────────────────────────────────────────────
 * {@code notification.uk_user_type_ref(user_id, type, ref_id)} 决定本事件对应的 {@code ref_id}
 * 必须是 **reviewId**，不能用 orderId：
 * 一个订单可以包含**多个商品**，买家在同一订单里写了 2 条评价、商家都回复，
 * 两条通知的 {@code (buyerId, type, orderId)} 会完全相同 → 第二条被静默吞掉。
 * reviewId 对每条评价天然唯一。
 */
@Data
public class ReviewRepliedEvent {

    /** 评价主键。消费侧通知的 ref_id，必须用它 */
    private Long reviewId;

    /** 订单主键（仅供文案展示，**不要**拿它当幂等键） */
    private Long orderId;

    /** 商品主键 */
    private Long productId;

    /** 商品名称（下单时的快照） */
    private String productName;

    /** 回复方：商品归属的卖家。消费侧用它填通知的 store_id，好让买家看到「来自店铺：xxx」 */
    private Long sellerId;

    /** 收件人：评价人（买家） */
    private Long buyerId;

    /** 商家回复内容 */
    private String replyContent;
}
