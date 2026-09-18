package com.user.bean;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 管理员建券请求。scopes 为空表示全场通用。 */
@Data
public class CouponCreateRequest {

    @NotBlank(message = "券名称不能为空")
    private String name;

    /** 1-满减，2-折扣 */
    @NotNull(message = "缺少券类型")
    private Integer couponType;

    /** 满减券必填：门槛与面额 */
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;

    /** 折扣券必填：折扣率（0.850 = 8.5 折） */
    private BigDecimal discountRate;
    /** 折扣封顶，可空表示不封顶 */
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "缺少发放总量")
    @Min(value = 1, message = "发放总量须大于 0")
    private Integer totalCount;

    @NotNull(message = "缺少生效开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "缺少生效结束时间")
    private LocalDateTime endTime;

    /** 适用范围；为空 = 全场通用 */
    private List<Scope> scopes;

    @Data
    public static class Scope {
        /** 1-商品，2-分类 */
        @NotNull
        private Integer scopeType;
        @NotNull
        private Long scopeId;
    }
}
