package com.user.service.impl;

import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.user.bean.Coupon;
import com.user.bean.CouponScope;
import com.user.bean.UsableCouponVO;
import com.user.bean.UserCoupon;
import com.user.mapper.CouponMapper;
import com.user.mapper.UserCouponMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 抵扣计算的守卫测试。
 *
 * 为什么需要它：这是全仓唯一一处**会算错钱**的地方，而且错法都很安静——
 * 折扣率乘出来是无限小数（不显式 setScale 会攒出一分钱级别的对不上账）、
 * 满减面额被配得比商品金额还大（不夹一下会**倒找钱**给买家）、
 * 范围匹配把不参与的商品也算进门槛（券会比设计的更容易用出去）。
 * 这些都不会抛异常，只会长期悄悄地多抵或少抵。
 *
 * 纯计算，不连库（user 服务没有 Docker 依赖）。
 */
class CouponServiceImplTest {

    private CouponMapper couponMapper;
    private UserCouponMapper userCouponMapper;
    private CouponServiceImpl service;

    private static final long USER_ID = 7L;
    private static final long USER_COUPON_ID = 100L;
    private static final long COUPON_ID = 9L;

    @BeforeEach
    void setUp() {
        couponMapper = mock(CouponMapper.class);
        userCouponMapper = mock(UserCouponMapper.class);
        service = new CouponServiceImpl();
        service.couponMapper = couponMapper;
        service.userCouponMapper = userCouponMapper;

        UserCoupon held = new UserCoupon();
        held.setId(USER_COUPON_ID);
        held.setCouponId(COUPON_ID);
        held.setStatus(UserCoupon.STATUS_UNUSED);
        when(userCouponMapper.selectByIdAndUser(anyLong(), anyLong())).thenReturn(held);
    }

    @Test
    @DisplayName("满减：达门槛按面额抵扣；未达门槛不可用")
    void thresholdCoupon() {
        stubCoupon(coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("100").discountAmount("20").build());

        CouponPreviewResult ok = service.preview(USER_ID, request(line(1L, null, "150.00")));
        assertTrue(ok.isUsable());
        assertEquals(new BigDecimal("20.00"), ok.getDiscountAmount());

        CouponPreviewResult tooLittle = service.preview(USER_ID, request(line(1L, null, "99.99")));
        assertFalse(tooLittle.isUsable(), "未达门槛必须判不可用");
        assertEquals(BigDecimal.ZERO, tooLittle.getDiscountAmount());
    }

    @Test
    @DisplayName("满减面额超过商品金额时被夹到小计，绝不倒找钱")
    void thresholdCouponNeverExceedsBase() {
        // 「满 10 减 100」——配置错误，但不是拒绝服务，而是封顶到实际商品金额
        stubCoupon(coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("10").discountAmount("100").build());

        CouponPreviewResult r = service.preview(USER_ID, request(line(1L, null, "30.00")));
        assertTrue(r.isUsable());
        assertEquals(new BigDecimal("30.00"), r.getDiscountAmount(), "抵扣不得超过范围内小计");
    }

    @Test
    @DisplayName("折扣：抵扣 = 范围内小计 × (1-折扣率)，两位小数四舍五入")
    void discountCouponRounding() {
        stubCoupon(coupon(Coupon.TYPE_DISCOUNT).discountRate("0.850").build());

        // 33.33 × 0.15 = 4.9995 -> 5.00
        CouponPreviewResult r = service.preview(USER_ID, request(line(1L, null, "33.33")));
        assertTrue(r.isUsable());
        assertEquals(new BigDecimal("5.00"), r.getDiscountAmount());
    }

    @Test
    @DisplayName("折扣封顶生效")
    void discountCouponCap() {
        stubCoupon(coupon(Coupon.TYPE_DISCOUNT).discountRate("0.500").maxDiscountAmount("15.00").build());

        // 100 × 0.5 = 50，但封顶 15
        CouponPreviewResult r = service.preview(USER_ID, request(line(1L, null, "100.00")));
        assertTrue(r.isUsable());
        assertEquals(new BigDecimal("15.00"), r.getDiscountAmount());
    }

    @Test
    @DisplayName("指定商品限定：只把命中的行计入门槛与小计")
    void productScopeOnlyCountsMatchedLines() {
        stubCouponWithScopes(coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("100").discountAmount("20").build(),
                scope(CouponScope.TYPE_PRODUCT, 1L));

        // 只有商品 1 命中（60 元），另 100 元不属于适用范围 -> 合计 60 未达 100 门槛
        CouponPreviewResult r = service.preview(USER_ID,
                request(line(1L, null, "60.00"), line(2L, null, "100.00")));
        assertFalse(r.isUsable(), "未命中的商品不得计入门槛");
    }

    @Test
    @DisplayName("指定分类限定：按明细的 categoryId 命中")
    void categoryScopeMatches() {
        stubCouponWithScopes(coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("100").discountAmount("20").build(),
                scope(CouponScope.TYPE_CATEGORY, 5L));

        CouponPreviewResult r = service.preview(USER_ID,
                request(line(1L, 5L, "120.00"), line(2L, 6L, "999.00")));
        assertTrue(r.isUsable());
        assertEquals(new BigDecimal("20.00"), r.getDiscountAmount());
    }

    @Test
    @DisplayName("无范围记录 = 全场通用，全部明细计入")
    void emptyScopesMeansAllProducts() {
        stubCouponWithScopes(coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("100").discountAmount("20").build());

        CouponPreviewResult r = service.preview(USER_ID,
                request(line(1L, null, "60.00"), line(2L, 9L, "60.00")));
        assertTrue(r.isUsable());
        assertEquals(new BigDecimal("20.00"), r.getDiscountAmount());
    }

    @Test
    @DisplayName("券已使用 / 无范围命中 / 场外商品 -> 判不可用且抵扣为 0")
    void rejectsUnusable() {
        stubCouponWithScopes(coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("100").discountAmount("20").build(),
                scope(CouponScope.TYPE_PRODUCT, 1L));

        CouponPreviewResult noMatch = service.preview(USER_ID, request(line(99L, null, "500.00")));
        assertFalse(noMatch.isUsable(), "没有命中任何适用商品应判不可用");
        assertEquals(BigDecimal.ZERO, noMatch.getDiscountAmount());

        UserCoupon used = new UserCoupon();
        used.setCouponId(COUPON_ID);
        used.setStatus(UserCoupon.STATUS_USED);
        when(userCouponMapper.selectByIdAndUser(anyLong(), anyLong())).thenReturn(used);
        assertFalse(service.preview(USER_ID, request(line(1L, null, "500.00"))).isUsable(),
                "已使用的券不得再试算通过");
    }

    @Test
    @DisplayName("券面文案用朴素十进制：不得出现 1E+2 这类科学计数法")
    void ruleTextNeverUsesScientificNotation() {
        UserCoupon held = new UserCoupon();
        held.setId(USER_COUPON_ID);
        held.setCouponId(COUPON_ID);
        held.setStatus(UserCoupon.STATUS_UNUSED);
        when(userCouponMapper.selectMine(USER_ID, UserCoupon.STATUS_UNUSED)).thenReturn(List.of(held));
        when(couponMapper.selectByIds(any())).thenReturn(List.of(
                coupon(Coupon.TYPE_THRESHOLD).thresholdAmount("100.00").discountAmount("20.00").build()));

        List<UsableCouponVO> list = service.usableCoupons(USER_ID, List.of(line(1L, null, "150.00")));

        assertEquals(1, list.size());
        assertEquals("满100减20", list.get(0).getRule(),
                "stripTrailingZeros() 把 100.00 变成 1E+2，直接拼进字符串就是「满1E+2减2E+1」——"
                        + "这个 bug 真的漏到过结算页上，必须走 toPlainString()");
    }

    // ---------- 构造辅助 ----------

    private void stubCoupon(Coupon c) {
        stubCouponWithScopes(c);
    }

    private void stubCouponWithScopes(Coupon c, CouponScope... scopes) {
        when(couponMapper.selectById(COUPON_ID)).thenReturn(c);
        when(couponMapper.selectScopes(any())).thenReturn(List.of(scopes));
    }

    private static CouponPreviewRequest request(CouponPreviewRequest.Line... lines) {
        CouponPreviewRequest req = new CouponPreviewRequest();
        req.setUserCouponId(USER_COUPON_ID);
        req.setLines(List.of(lines));
        return req;
    }

    private static CouponPreviewRequest.Line line(Long productId, Long categoryId, String lineTotal) {
        CouponPreviewRequest.Line l = new CouponPreviewRequest.Line();
        l.setProductId(productId);
        l.setCategoryId(categoryId);
        l.setLineTotal(new BigDecimal(lineTotal));
        return l;
    }

    private static CouponScope scope(int type, Long scopeId) {
        CouponScope s = new CouponScope();
        s.setScopeType(type);
        s.setScopeId(scopeId);
        return s;
    }

    /** 小建造器：只写关心的字段，其余保持「不参与本次用例」的中性值 */
    private static CouponBuilder coupon(int type) {
        return new CouponBuilder(type);
    }

    private static final class CouponBuilder {
        private final Coupon c = new Coupon();

        CouponBuilder(int type) {
            c.setId(COUPON_ID);
            c.setCouponType(type);
            c.setStatus(Coupon.STATUS_ENABLED);
            c.setThresholdAmount(BigDecimal.ZERO);
            c.setDiscountAmount(BigDecimal.ZERO);
            c.setDiscountRate(BigDecimal.ONE);
            c.setStartTime(LocalDateTime.now().minusDays(1));
            c.setEndTime(LocalDateTime.now().plusDays(1));
        }

        CouponBuilder thresholdAmount(String v) {
            c.setThresholdAmount(new BigDecimal(v));
            return this;
        }

        CouponBuilder discountAmount(String v) {
            c.setDiscountAmount(new BigDecimal(v));
            return this;
        }

        CouponBuilder discountRate(String v) {
            c.setDiscountRate(new BigDecimal(v));
            return this;
        }

        CouponBuilder maxDiscountAmount(String v) {
            c.setMaxDiscountAmount(new BigDecimal(v));
            return this;
        }

        Coupon build() {
            return c;
        }
    }
}
