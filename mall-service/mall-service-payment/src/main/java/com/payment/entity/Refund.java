package com.payment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单（refund，payment 库）：资金视角的退款执行记录。
 *
 * 与 order 库的 order_refund（审核视角）通过 refund_no 一对一关联：
 *  - order_refund 记的是「买家申请了、卖家审没审过」；
 *  - 本表记的是「这笔钱从渠道退出去没有」。
 * 故本表 refund_status 只有三态（0退款中 / 1退款成功 / 2退款失败），
 * 不含「待审核」——审核通过之前不会有本表记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("refund")
public class Refund {

    @TableField("id")
    private Long id;

    /** 退款单号（与 order 库 order_refund.refund_no 对齐） */
    @TableField("refund_no")
    private String refundNo;

    @TableField("order_id")
    private Long orderId;

    /** 原支付成功的支付单 id */
    @TableField("pay_order_id")
    private Long payOrderId;

    /** 买家用户 id */
    @TableField("user_id")
    private Long userId;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /** 0-退款中，1-退款成功，2-退款失败（审核驳回也落 2） */
    @TableField("refund_status")
    private Integer refundStatus;

    /** 退款原因（买家申请时填写，透传渠道） */
    @TableField("refund_reason")
    private String refundReason;

    /** 退款完成时间 */
    @TableField("refund_time")
    private LocalDateTime refundTime;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
