package com.order.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款申请单（order_refund）：买家申请 -> 卖家/管理员审核 -> payment 打款 -> 订单置已退款。
 * 主键 id 由 DB 自增生成（仓库惯例：全字段 @TableField + @Options 回填）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_refund")
public class OrderRefund {

    @TableField("id")
    private Long id;

    /** 退款单号（业务唯一，与 payment.refund.refund_no 对齐） */
    @TableField("refund_no")
    private String refundNo;

    @TableField("order_id")
    private Long orderId;

    /** 订单编号（冗余，便于按单号排查） */
    @TableField("order_no")
    private String orderNo;

    /** 申请买家 id */
    @TableField("user_id")
    private Long userId;

    /** 退款金额（本仓为整单全额退款 = orders.total_amount） */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /** 审核状态，见 {@link com.model.enums.RefundAuditStatus}：0待审核 1退款中 2已退款 3已驳回 */
    @TableField("refund_status")
    private Integer refundStatus;

    /** 买家退款原因 */
    @TableField("refund_reason")
    private String refundReason;

    /** 审核驳回原因 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 审核人 id（卖家或管理员） */
    @TableField("audit_user_id")
    private Long auditUserId;

    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("refund_time")
    private LocalDateTime refundTime;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("updated_time")
    private LocalDateTime updatedTime;

    // ---------- 非持久化：列表联表带出的订单/买家信息 ----------

    /** 订单状态（联表 orders.order_status），供审核列表展示申请前的订单语境 */
    @TableField(exist = false)
    private Integer orderStatus;

    /** 下单买家用户名（联表 user.username） */
    @TableField(exist = false)
    private String buyerName;
}
