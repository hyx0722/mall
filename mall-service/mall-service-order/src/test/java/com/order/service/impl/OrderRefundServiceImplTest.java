package com.order.service.impl;

import com.model.bean.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 退款金额口径的守卫测试：**退款只能退实付，不能退原价**。
 *
 * 这是最容易「看起来对、只有对账时才发现」的一类错误：
 * orders.total_amount 记的是原价合计，优惠单独记在 discount_amount，
 * 支付侧按 total-discount 收款。若退款按 totalAmount 发起，用券订单会多退优惠金额——
 * mock 渠道下直接多出钱，真实渠道则因「退款金额大于支付金额」被拒、退款单卡死。
 *
 * 纯计算，不连库、不依赖 Spring。
 */
class OrderRefundServiceImplTest {

    @Test
    @DisplayName("用券订单：退款金额 = 原价 − 优惠")
    void refundsNetPaidAmount() {
        Order order = new Order();
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setDiscountAmount(new BigDecimal("20.00"));

        assertEquals(0, OrderRefundServiceImpl.netPaidAmount(order).compareTo(new BigDecimal("80.00")),
                "退款必须按实付 80 元，而不是原价 100 元");
    }

    @Test
    @DisplayName("未用券：退款金额 = 原价")
    void noDiscountMeansFullAmount() {
        Order order = new Order();
        order.setTotalAmount(new BigDecimal("59.90"));
        order.setDiscountAmount(BigDecimal.ZERO);

        assertEquals(0, OrderRefundServiceImpl.netPaidAmount(order).compareTo(new BigDecimal("59.90")));
    }

    @Test
    @DisplayName("discount_amount 为 null（历史数据）：按无优惠处理")
    void nullDiscountIsZero() {
        Order order = new Order();
        order.setTotalAmount(new BigDecimal("30.00"));
        order.setDiscountAmount(null);

        assertEquals(0, OrderRefundServiceImpl.netPaidAmount(order).compareTo(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("优惠大于原价（脏数据）：夹到 0，绝不返回负数")
    void discountNeverMakesRefundNegative() {
        Order order = new Order();
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setDiscountAmount(new BigDecimal("15.00"));

        assertEquals(0, OrderRefundServiceImpl.netPaidAmount(order).signum(),
                "负的退款金额会被渠道当成非法参数，且语义上也不该出现");
    }

    @Test
    @DisplayName("金额为 null：按 0 处理")
    void nullAmountsAreZero() {
        Order order = new Order();
        order.setTotalAmount(null);
        order.setDiscountAmount(null);

        assertEquals(0, OrderRefundServiceImpl.netPaidAmount(order).signum());
    }

    @Test
    @DisplayName("分位金额相减不丢精度")
    void keepsCentPrecision() {
        Order order = new Order();
        order.setTotalAmount(new BigDecimal("99.99"));
        order.setDiscountAmount(new BigDecimal("0.50"));

        assertEquals(0, OrderRefundServiceImpl.netPaidAmount(order).compareTo(new BigDecimal("99.49")));
    }
}
