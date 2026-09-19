package com.model.event;

import lombok.Data;

/**
 * 订单已发货事件（order 服务 -> RabbitMQ -> 通知等下游）。
 *
 * 买家确认收货（{@code order_status} 2待收货）此前只有一个正向事件 {@link OrderCompletedEvent}，
 * 「已发货」这一档完全静默：卖家点了发货，买家那头没有任何信号。本事件补上这一环。
 *
 * ── ⚠️ 只在「整单都发完」时发布，绝不是每卖一次发一次 ──────────────
 * 发货是**多卖家**流程：每个卖家各写一条 {@code shipping} 行，只有最后一个卖家发货时
 * OrderServiceImpl 才会把整单 1待发货 -> 2待收货。
 *
 * 本事件必须挂在那个「整单翻转」的分支上（{@code markFullyShipped} 影响行数 > 0）。
 * 若改成每个卖家发一次，消费侧的通知去重键 {@code (user_id, type, ref_id)} 会把
 * **第二个及之后卖家的通知整条吞掉**——是**静默丢失**，不是重复。重复至少还看得见，
 * 丢失没有任何信号，因此这里的选择不是风格偏好而是正确性要求。
 *
 * 代价：多卖家订单的事件体里只带**最后一个**卖家的运单号。买家要看全部发货单
 * 得进订单详情（那里读 {@code shippings} 全表）。故消费侧的文案不能依赖运单号一定存在。
 */
@Data
public class OrderShippedEvent {

    /** 业务订单号 */
    private String orderNo;

    /** 订单主键 id */
    private Long orderId;

    /** 买家用户 id */
    private Long userId;

    /**
     * 物流公司 / 运单号：来自**最后一个**发货的卖家，且 {@code ShipRequest} 上两个字段都可空。
     * 消费侧必须容忍 null（文案退化成「订单已发货」即可，不要拼出「null 的 null」）。
     */
    private String logisticsCompany;

    private String trackingNo;
}
