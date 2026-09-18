package com.user.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 结算页「可用券」列表项：把券基本信息 + 对本单的评估结果合成一条，
 * 前端一次请求即可渲染券选择器，不必为每张券各发一次试算。
 *
 * 不可用的券也会返回（{@code usable=false} + {@code reason}），
 * 让用户知道「我有这张券但为什么用不了」，而不是让它凭空消失。
 */
@Data
public class UsableCouponVO {

    private Long userCouponId;

    private Long couponId;

    private String name;

    /** 1-满减，2-折扣 */
    private Integer couponType;

    /** 展示文案：满100减20 / 8.5折 */
    private String rule;

    private boolean usable;

    /** 可用时的抵扣金额；不可用时为 0 */
    private BigDecimal deduction;

    /** 不可用原因；可用时为 null */
    private String reason;
}
