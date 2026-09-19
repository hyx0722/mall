package com.payment.metrics;

import com.payment.mapper.RefundMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

/**
 * 悬挂退款指标：{@code mall.pay.refund.stuck} = 停在「退款中」超过阈值的退款单数。
 *
 * 补的是本仓一处自认的观测缺口（docs/operations.md 的退款已知边界）：渠道调用失败时
 * {@code executeRefund} 刻意抛异常而不置失败，靠消息有界重试兜瞬时故障，重试耗尽后落 DLQ，
 * 由 {@code RefundReconcileTask} 每 5 分钟捞回来重投。在此之前，这条链路的健康状况
 * **只能从任务打出的 WARN 日志推断**——而日志无法区分「在跑但一笔都没捞回」与「根本没在跑」。
 *
 * 查询走 {@link RefundMapper#countStuckRefunding}，与 {@code RefundReconcileTask} 用同一个
 * 阈值配置（都读 {@code payment.refund-reconcile-minutes}），保证指标与任务对「悬挂」的口径一致。
 *
 * 阈值大于消费重试的最长耗时，因此该值 > 0 即意味着确实有单需要人工关注；
 * 持续 > 0 说明渠道侧一直拒绝出款，而不是瞬时抖动。
 */
@Slf4j
public class RefundMetrics implements MeterBinder {

    private final RefundMapper refundMapper;
    private final int stuckMinutes;

    public RefundMetrics(RefundMapper refundMapper, int stuckMinutes) {
        this.refundMapper = refundMapper;
        this.stuckMinutes = stuckMinutes;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("mall.pay.refund.stuck", this::countStuck)
                .description("停在「退款中」超过 " + stuckMinutes + " 分钟的退款单数；"
                        + "由 RefundReconcileTask 重投，持续 >0 说明渠道侧一直未出款")
                .register(registry);
    }

    private double countStuck() {
        try {
            return refundMapper.countStuckRefunding(stuckMinutes);
        } catch (Exception e) {
            // 同 OutboxMetrics：指标采集不能反过来影响业务，失败时让采样点缺失
            log.warn("[metrics] 统计悬挂退款失败: {}", e.getMessage());
            return Double.NaN;
        }
    }
}
