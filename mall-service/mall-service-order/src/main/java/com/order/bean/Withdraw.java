package com.order.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家提现申请。
 *
 * 可提现余额的口径（**不靠 mutate settlement 来记账**）：
 * <pre>
 *   可提现余额 = Σ(settlement.net_amount where status=1 可提现)
 *              − Σ(withdraw.amount  where status=0 待审核)      ← 申请中先占住，防止重复申请
 * </pre>
 * 审核通过时才把该商家在 {@code apply_time} 之前的可提现明细置为 {@code status=2 已提现}，
 * 于是 Σ(status=1) 自然下降，账目自洽。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("withdraw")
public class Withdraw {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_PAID = 1;
    public static final int STATUS_REJECTED = 2;

    @TableField(value = "id")
    private Long id;

    @TableField(value = "withdraw_no")
    private String withdrawNo;

    @TableField(value = "seller_id")
    private Long sellerId;

    @TableField(value = "amount")
    private BigDecimal amount;

    @TableField(value = "status")
    private Integer status;

    @TableField(value = "audit_user_id")
    private Long auditUserId;

    @TableField(value = "audit_time")
    private LocalDateTime auditTime;

    @TableField(value = "reject_reason")
    private String rejectReason;

    /** 申请时间，也是「这次提现覆盖到哪些结算明细」的分界线 */
    @TableField(value = "apply_time")
    private LocalDateTime applyTime;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;

    @TableField(value = "updated_time")
    private LocalDateTime updatedTime;
}
