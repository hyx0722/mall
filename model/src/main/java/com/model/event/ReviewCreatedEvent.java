package com.model.event;

import lombok.Data;

/**
 * 商品评价已创建事件（order 服务 -> RabbitMQ -> user 服务写站内通知）。
 *
 * 买家在「已完成」的订单上给某个商品写完评价、与评价行**同一本地事务**写入 outbox 后投递，
 * 收件人是该商品的卖家。
 *
 * ── 为什么不消费 {@link OrderCompletedEvent} ──────────────────────────
 * 那个事件的注释里写着「商品评价：只有已完成才允许评价」，说的是**资格判定的依据**：
 * 评价以「订单已完成」为前提。但评价本身是**同步写入**的——需要用户当场输入评分和文字，
 * 不可能由事件触发。所以本事件是评价**写完之后**的通知信号，与 OrderCompletedEvent
 * 没有消费关系。**不要**去补一个不存在的 order.completed 评价消费者。
 *
 * ── 消费侧的幂等键 ────────────────────────────────────────────────
 * user 侧的通知靠 {@code notification.uk_user_type_ref(user_id, type, ref_id)} 去重。
 * 本事件对应的 {@code ref_id} 必须是 **reviewId**：
 * 若用 productId，商家对同一个商品永远只能收到**第一条**评价通知，
 * 之后的评价全部撞唯一键被静默丢弃——而「买两次可评两次」正是这个功能的语义。
 */
@Data
public class ReviewCreatedEvent {

    /** 评价主键。消费侧通知的 ref_id，必须用它 */
    private Long reviewId;

    /** 订单主键 */
    private Long orderId;

    /** 商品主键 */
    private Long productId;

    /** 商品名称（下单时的快照），用于通知文案免回查 */
    private String productName;

    /** 收件人：商品归属的卖家 */
    private Long sellerId;

    /** 评价人（买家）用户名，用于通知文案免回查 */
    private String buyerName;

    /** 评分 1-5 */
    private Integer rating;

    /**
     * 评价正文（最长 500 字）。
     *
     * <p>⚠️ 消费侧**必须截断**再写进通知：{@code notification.content} 是 {@code VARCHAR(500)}，
     * 而这个字段本身上限就是 500——整条拼上前缀会溢出，严格模式下抛异常 →
     * 重试耗尽 → 落 DLQ，把真正的毒消息埋掉。
     */
    private String content;
}
