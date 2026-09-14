package com.model.event;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款申请/审核事件（order 服务 -> RabbitMQ -> payment 服务）。
 *
 * 退款的状态机主人是 order（orders.order_status 5退款中 / 6已退款），
 * payment 侧只负责「按指令维护退款单并调渠道打款」，故三种动作共用一个事件体，
 * 由 {@link #action} 区分，payment 侧单监听器分派：
 *
 *  - {@link #ACTION_APPLY}  ：买家申请退款成功落库后发出，payment 据此建 refund 单（refund_status=0 退款中）；
 *  - {@link #ACTION_APPROVE}：卖家/管理员审核通过，payment 据此调渠道退款；
 *  - {@link #ACTION_REJECT} ：审核驳回，payment 据此把 refund 单置 2-退款失败。
 *
 * 关联键是 orderId + refundNo：refundNo 由 order 侧生成并落 order_refund 表，
 * 两端用它对齐同一笔退款单，避免 payment 侧重复建单。
 */
@Data
public class RefundRequestEvent {

    /** 买家申请退款：payment 建退款单 */
    public static final String ACTION_APPLY = "APPLY";
    /** 审核通过：payment 调渠道退款 */
    public static final String ACTION_APPROVE = "APPROVE";
    /** 审核驳回：payment 把退款单置为失败 */
    public static final String ACTION_REJECT = "REJECT";

    /** 动作：APPLY / APPROVE / REJECT */
    private String action;

    /** 退款单号（order 侧生成，业务唯一，两端对齐用） */
    private String refundNo;

    /** 业务订单号 */
    private String orderNo;

    /** 订单主键 id */
    private Long orderId;

    /** 买家用户 id */
    private Long userId;

    /** 退款金额（本仓为整单全额退款 = orders.total_amount） */
    private BigDecimal refundAmount;

    /** 买家填写的退款原因（APPLY 时有值） */
    private String refundReason;

    /** 审核驳回原因（REJECT 时有值） */
    private String rejectReason;
}
