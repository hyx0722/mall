package com.user.service.impl;

import com.model.bean.CouponPreviewRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CouponPreviewRequest} 的校验契约守卫测试。
 *
 * 为什么需要它：这个 DTO 被**两个契约不同**的接口共用——
 * {@code /coupon/preview}（order 服务调用，必带 userCouponId）与
 * {@code /coupon/usable}（结算页拉可用券列表，**只传 lines**）。
 *
 * 一旦有人「顺手」给 userCouponId 加上 @NotNull（看起来完全合理：试算当然要指定券），
 * 后者就会一律 400。而结算页为了「拉不到券不影响结算」把异常吞掉了，
 * 于是表现为**券卡片永远不出现**，只有一条被忽略的报错——从现象根本反推不到 DTO 上的一个注解。
 *
 * 纯校验，不起 Spring 上下文、不连库。
 */
class CouponPreviewRequestValidationTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("/coupon/usable 只传 lines：必须通过校验（否则结算页的券卡片永远不渲染）")
    void validWithoutUserCouponId() {
        CouponPreviewRequest request = new CouponPreviewRequest();
        request.setLines(List.of(line()));

        assertTrue(VALIDATOR.validate(request).isEmpty(),
                "不带 userCouponId 时必须校验通过——/coupon/usable 就是这么调用的。"
                        + "若这里失败，说明有人给它加了 @NotNull，结算页选券会整体失效");
    }

    @Test
    @DisplayName("空明细与缺字段仍必须被拒——校验不能因为上面那条而整体失效")
    void stillRejectsInvalidPayloads() {
        CouponPreviewRequest emptyLines = new CouponPreviewRequest();
        emptyLines.setLines(List.of());
        assertFalse(VALIDATOR.validate(emptyLines).isEmpty(), "空明细必须被拒");

        CouponPreviewRequest nullLines = new CouponPreviewRequest();
        assertFalse(VALIDATOR.validate(nullLines).isEmpty(), "缺 lines 必须被拒");

        CouponPreviewRequest badLine = new CouponPreviewRequest();
        CouponPreviewRequest.Line noTotal = new CouponPreviewRequest.Line();
        noTotal.setProductId(1L);
        badLine.setLines(List.of(noTotal));
        assertFalse(VALIDATOR.validate(badLine).isEmpty(), "明细缺 lineTotal 必须被拒");
    }

    private static CouponPreviewRequest.Line line() {
        CouponPreviewRequest.Line line = new CouponPreviewRequest.Line();
        line.setProductId(1L);
        line.setCategoryId(2L);
        line.setLineTotal(new BigDecimal("50.00"));
        return line;
    }
}
