package com.order.bean;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家申请退款请求：整单全额退款，只需订单 id + 原因。
 * 金额不由前端传入（取 orders.total_amount），避免被篡改。
 */
@Data
public class RefundApplyRequest {

    @NotNull(message = "订单 id 不能为空")
    private Long orderId;

    /** 退款原因（可空，最多 255） */
    @Size(max = 255, message = "退款原因过长")
    private String reason;
}
