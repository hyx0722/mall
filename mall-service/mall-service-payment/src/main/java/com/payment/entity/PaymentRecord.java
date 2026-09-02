package com.payment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付回调记录（payment_record）：第三方渠道异步通知 / 模拟通知的原始报文落库，便于对账。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment_record")
public class PaymentRecord {
    @TableField("id")
    private Long id;
    @TableField("pay_order_id")
    private Long payOrderId;
    @TableField("pay_no")
    private String payNo;
    @TableField("transaction_id")
    private String transactionId;
    /** 回调类型：支付通知/退款通知 */
    @TableField("notify_type")
    private String notifyType;
    /** 第三方回调原始报文 */
    @TableField("notify_content")
    private String notifyContent;
    /** 处理状态：0-未处理，1-处理成功，2-处理失败 */
    @TableField("handle_status")
    private Integer handleStatus;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
