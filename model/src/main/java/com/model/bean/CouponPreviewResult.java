package com.model.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用券试算结果（user 服务返回给 order 服务）。
 *
 * {@code usable=false} 时 {@code discountAmount} 为 0、{@code reason} 说明原因——
 * 调用方据此抛业务异常中止下单，而不是自己判断规则。
 * **券的可用性规则只在 user 服务实现一处**，order 侧不复制任何判定逻辑。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponPreviewResult {

    private boolean usable;

    private BigDecimal discountAmount;

    /** 不可用原因（可用时为 null）；直接面向用户，不要泄露内部细节 */
    private String reason;
}
