package com.payment.channel;

import lombok.Data;

/**
 * 渠道异步通知校验后的提取结果：null 表示校验失败（或渠道未启用），不推进支付。
 */
@Data
public class NotifyResult {
    /** 支付单号（渠道 out_trade_no） */
    private String payNo;
    /** 第三方流水号 */
    private String transactionId;
}
