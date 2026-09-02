package com.payment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单（pay_order）：一笔订单一个待支付单，落库时 payment_status=0。
 * 主键 id 由 DB 自增生成，不设 @TableId（仓库惯例：全部字段 @TableField + @Options 回填）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("pay_order")
public class PayOrder {
    @TableField("id")
    private Long id;
    /** 支付单号（业务唯一，同时作为发给第三方渠道的 out_trade_no） */
    @TableField("pay_no")
    private String payNo;
    /** 关联订单 id（逻辑外键 -> order_db.orders.id） */
    @TableField("order_id")
    private Long orderId;
    /** 买家用户 id（逻辑外键 -> user_db.user.id） */
    @TableField("user_id")
    private Long userId;
    /** 支付金额（实付金额，取自订单） */
    @TableField("pay_amount")
    private BigDecimal payAmount;
    /** 支付方式：1-支付宝，2-微信 */
    @TableField("payment_method")
    private Integer paymentMethod;
    /** 支付状态：0-待支付，1-支付成功，2-已退款，3-支付失败 */
    @TableField("payment_status")
    private Integer paymentStatus;
    /** 第三方支付流水号（支付宝交易号 / 微信支付单号） */
    @TableField("transaction_id")
    private String transactionId;
    @TableField("pay_time")
    private LocalDateTime payTime;
    @TableField("expire_time")
    private LocalDateTime expireTime;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
