package com.payment.bean;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建支付单请求：绑定一笔待付款订单并指定支付渠道。
 */
@Data
public class CreatePayOrderRequest {

    /** 关联订单 id（经 order 服务 findDetailOrder 校验归属） */
    @NotNull(message = "订单id不能为空")
    private Long orderId;

    /** 支付方式：1-支付宝，2-微信 */
    @NotNull(message = "支付方式不能为空")
    @Min(value = 1, message = "支付方式不合法")
    private Integer paymentMethod;
}
