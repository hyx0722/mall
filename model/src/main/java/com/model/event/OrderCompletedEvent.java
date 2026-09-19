package com.model.event;

import lombok.Data;

import java.util.List;

/**
 * 订单已完成事件（order 服务 -> RabbitMQ -> 结算 / 评价 等下游）。
 *
 * 买家确认收货（{@code order_status} 2待收货 -> 3已完成）时，order 服务与状态翻转
 * **同事务**写入 outbox 后投递。
 *
 * ── 为什么需要它 ────────────────────────────────────────────────────
 * 收货此前是**纯本地状态机推进**（订单服务的条件 UPDATE），不发任何事件：
 * 正向链路走到「已完成」就断了，下游无从得知「这单彻底结束、不会再退」。
 * 而两件事都以此为起点：
 * <ul>
 *   <li><b>商家结算</b>：只有已完成且过了售后期，卖家的钱才该可提现；</li>
 *   <li><b>商品评价</b>：只有已完成才允许评价。</li>
 * </ul>
 *
 * ⚠️ 上面第二条说的是**资格判定的依据**，不是「评价要消费这个事件」——
 * 评价由买家当场输入评分与文字、**同步写入**（{@code ProductReviewService.create}），
 * 本事件只在评价写完后用于通知。**不要**去补一个 order.completed 的评价消费者。
 *
 * 与 {@link OrderRefundedEvent} 的区别：那个是**逆向**（退款到账，库存回补，change_type=6）；
 * 本事件是**正向终态**，不涉及库存变动，只表示「交易闭环完成」。
 *
 * ⚠️ 本事件**必须始终有队列绑定**：各服务已开 {@code template.mandatory}，
 * 没有队列可路由的消息会被退回、重试耗尽后置为 outbox {@code status=3 已放弃}，
 * 并持续推高 {@code mall.outbox.unroutable}。当前的消费者是 order 自身的结算
 * （{@code OrderCompletedListener} -> {@code SettlementService}）。
 */
@Data
public class OrderCompletedEvent {

    /** 业务订单号 */
    private String orderNo;

    /** 订单主键 id */
    private Long orderId;

    /** 买家用户 id */
    private Long userId;

    /**
     * 订单明细（商品与数量）。
     * 结算与评价都要落到行级，带上可省一次回查；金额不在此处——实付金额受优惠分摊影响，
     * 由消费方按订单快照自行计算，避免事件体与订单表两处口径漂移。
     */
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
