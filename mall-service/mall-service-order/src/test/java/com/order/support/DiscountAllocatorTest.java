package com.order.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 优惠分摊的守卫测试。这是结算（A1）与部分退款（A5）**共用**的金额逻辑。
 *
 * 需要守住的是「合计精确」这条不变量：按比例分摊几乎必然除不尽，
 * 若每行各自四舍五入，Σ 分摊 会与整单优惠差几分钱。这个差不会抛异常，
 * 只会让「明细加起来 ≠ 订单总额」，在结算和退款两条链路上各自表现为对不上账，
 * 而且要等有人手工核对时才会发现。
 *
 * 纯计算，不连库。
 */
class DiscountAllocatorTest {

    @Test
    @DisplayName("无优惠 / 整单为 0：全部记 0，不做除法")
    void noDiscountOrZeroTotal() {
        List<BigDecimal> lines = List.of(new BigDecimal("60.00"), new BigDecimal("40.00"));

        assertTrue(DiscountAllocator.allocate(new BigDecimal("100.00"), BigDecimal.ZERO, lines)
                .stream().allMatch(d -> d.signum() == 0), "无优惠时应全为 0");

        // totalAmount=0 时若照常做除法会 ArithmeticException（除零）
        List<BigDecimal> zeroTotal = DiscountAllocator.allocate(BigDecimal.ZERO, new BigDecimal("10.00"), lines);
        assertEquals(2, zeroTotal.size());
        assertTrue(zeroTotal.stream().allMatch(d -> d.signum() == 0), "整单为 0 时应全为 0 而不是抛异常");
    }

    @Test
    @DisplayName("按行占比分摊，且合计恰好等于整单优惠（尾差归末行）")
    void sumAlwaysEqualsTotalDiscount() {
        // 10 元优惠按 60:40 分 -> 6.00 / 4.00
        List<BigDecimal> r = DiscountAllocator.allocate(new BigDecimal("100.00"), new BigDecimal("10.00"),
                List.of(new BigDecimal("60.00"), new BigDecimal("40.00")));
        assertEquals(new BigDecimal("6.00"), r.get(0));
        assertEquals(new BigDecimal("4.00"), r.get(1));

        // 除不尽的情形：10 / 3 行 -> 3.33 / 3.33 / 3.34，合计必须正好 10.00
        List<BigDecimal> uneven = DiscountAllocator.allocate(new BigDecimal("90.00"), new BigDecimal("10.00"),
                List.of(new BigDecimal("30.00"), new BigDecimal("30.00"), new BigDecimal("30.00")));
        BigDecimal sum = uneven.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(new BigDecimal("10.00")),
                "Σ分摊必须恰好等于整单优惠，否则结算与退款会各自差出几分钱");
    }

    @Test
    @DisplayName("单行分摊不超过该行小计（否则该行实付为负 = 倒找钱）")
    void shareNeverExceedsLineTotal() {
        List<BigDecimal> r = DiscountAllocator.allocate(new BigDecimal("100.00"), new BigDecimal("100.00"),
                List.of(new BigDecimal("1.00"), new BigDecimal("99.00")));
        for (int i = 0; i < r.size(); i++) {
            assertTrue(r.get(i).compareTo(List.of(new BigDecimal("1.00"), new BigDecimal("99.00")).get(i)) <= 0,
                    "第 " + i + " 行的分摊超过了该行小计");
        }
    }

    @Test
    @DisplayName("空明细返回空列表，不抛异常")
    void emptyLines() {
        assertTrue(DiscountAllocator.allocate(new BigDecimal("100.00"), new BigDecimal("10.00"), List.of())
                .isEmpty());
    }
}
