package com.order.support;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 把整单优惠按行占比分摊到各订单明细。
 *
 * 为什么要分摊而不是「优惠就记在订单头上」：结算与部分退款都要求**行级**金额。
 * 卖家甲卖了 60 元的货、乙卖了 40 元、整单优惠 10 元，若不分摊，就无法回答
 * 「这 10 元优惠该从谁的货款里扣」。分摊比例用行原价占比，是最通用也最好解释的口径。
 *
 * ⚠️ 本类是**结算（A1）与部分退款（A5）共用的唯一分摊实现**——两处若各写一份，
 * 迟早会出现「结算按一套口径、退款按另一套」的差账。
 *
 * 舍入：每行先按比例四舍五入到分，**末行吸收全部尾差**，保证
 * {@code Σ(分摊) 恰好等于整单优惠}。不这样做会攒出几分钱的差，对账时表现为
 * 「明细加起来与订单总额对不上」，且永远查不出原因。
 */
@Slf4j
public final class DiscountAllocator {

    private static final int MONEY_SCALE = 2;

    private DiscountAllocator() {
    }

    /**
     * @param totalAmount    整单原价合计（用于算占比；为 0 时全部按 0 处理，避免除零）
     * @param discountAmount 整单优惠金额
     * @param lineTotals     各明细行的原价小计，顺序与返回值一一对应
     * @return 与 {@code lineTotals} 等长的分摊结果，每项不超过对应行的小计
     */
    public static List<BigDecimal> allocate(BigDecimal totalAmount, BigDecimal discountAmount,
                                            List<BigDecimal> lineTotals) {
        List<BigDecimal> result = new ArrayList<>(lineTotals.size());
        boolean noDiscount = discountAmount == null || discountAmount.signum() == 0;
        if (noDiscount || totalAmount == null || totalAmount.signum() == 0 || lineTotals.isEmpty()) {
            // 无优惠 / 整单为 0：全部记 0，不做除法
            for (int i = 0; i < lineTotals.size(); i++) {
                result.add(BigDecimal.ZERO.setScale(MONEY_SCALE));
            }
            return result;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < lineTotals.size(); i++) {
            BigDecimal lineTotal = nvl(lineTotals.get(i));
            BigDecimal share;
            if (i == lineTotals.size() - 1) {
                // 末行吸收尾差，保证合计精确
                share = discountAmount.subtract(allocated);
            } else {
                share = discountAmount.multiply(lineTotal)
                        .divide(totalAmount, MONEY_SCALE, RoundingMode.HALF_UP);
                allocated = allocated.add(share);
            }
            // 单行分摊不得超过该行小计，否则这行实付为负（等于倒找钱给买家）
            if (share.compareTo(lineTotal) > 0) {
                log.warn("[settlement] 行分摊 {} 超过行小计 {}，已夹到行小计", share, lineTotal);
                share = lineTotal;
            }
            if (share.signum() < 0) {
                share = BigDecimal.ZERO.setScale(MONEY_SCALE);
            }
            result.add(share);
        }
        return result;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
