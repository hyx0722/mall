package com.order.bean;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 商家发起提现申请。金额上限由服务端按可提现余额校验，客户端传不了更大的数也绕不过去。 */
@Data
public class WithdrawApplyRequest {

    @NotNull(message = "请填写提现金额")
    @DecimalMin(value = "0.01", message = "提现金额必须大于 0")
    private BigDecimal amount;
}
