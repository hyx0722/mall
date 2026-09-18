package com.order.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家结算明细：一笔订单明细一行，订单走到「已完成」时由 {@code OrderCompletedListener} 生成。
 *
 * 金额口径（务必与 {@link #grossAmount} 等字段注释一致）：
 * <pre>
 *   行实付 = gross_amount - discount_amount          // 整单优惠按行占比分摊
 *   commission_amount = 行实付 × 佣金率               // **按实付计佣，不按原价**——
 *                                                    // 否则平台会从自己让利出去的钱里再抽一份
 *   net_amount = 行实付 - commission_amount
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("settlement")
public class Settlement {

    /** 待结算：订单已完成，但账期（T+N）未到，还不能提现 */
    public static final int STATUS_PENDING = 0;
    /** 可提现：账期已到 */
    public static final int STATUS_WITHDRAWABLE = 1;
    /** 已提现：对应的提现申请已打款 */
    public static final int STATUS_WITHDRAWN = 2;

    @TableField(value = "id")
    private Long id;

    @TableField(value = "order_id")
    private Long orderId;

    @TableField(value = "order_no")
    private String orderNo;

    /** 幂等键：唯一索引 uk_order_item */
    @TableField(value = "order_item_id")
    private Long orderItemId;

    @TableField(value = "seller_id")
    private Long sellerId;

    @TableField(value = "product_id")
    private Long productId;

    @TableField(value = "gross_amount")
    private BigDecimal grossAmount;

    @TableField(value = "discount_amount")
    private BigDecimal discountAmount;

    @TableField(value = "commission_amount")
    private BigDecimal commissionAmount;

    @TableField(value = "net_amount")
    private BigDecimal netAmount;

    @TableField(value = "status")
    private Integer status;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;

    @TableField(value = "updated_time")
    private LocalDateTime updatedTime;
}
