package com.payment.bean;

import com.payment.channel.PayParams;
import com.payment.entity.PayOrder;
import lombok.Data;

/**
 * 建支付单结果：支付单 + 渠道下单参数（前端按 PayParams.contentType 渲染收银台）。
 */
@Data
public class CreatePayOrderVO {
    private PayOrder payOrder;
    private PayParams payParams;
}
