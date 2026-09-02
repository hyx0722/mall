package com.payment.bean;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟支付成功请求（仅 payment.mock.enabled=true 时可用，测试钩子）。
 */
@Data
public class MockSuccessRequest {
    @NotNull(message = "支付单号不能为空")
    private String payNo;
}
