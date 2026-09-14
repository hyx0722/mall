package com.mall.common.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 事务发件箱积压指标：{@code mall.outbox.pending} = outbox 表中 status=0（待投递）的行数。
 *
 * 为什么需要它：outbox 由各服务的 relay 定时领取投递（每 3s 一批），一旦 relay 停摆或持续投递失败，
 * 事件会静静堆积在表里而**没有任何外部表征**——表现为订单不推进、库存不释放、支付状态不回写，
 * 排查时只能从业务现象倒推。有这条指标后，积压量可直接观测，配一条阈值告警就能第一时间发现。
 *
 * 注意：只有配备 outbox 表的服务（order / payment）才能导入本类，
 * 其余服务的库里没有该表，查询会抛异常（已在 {@link #countPending} 内兜住，但指标会恒为 NaN）。
 */
@Slf4j
public class OutboxMetrics implements MeterBinder {

    private final JdbcTemplate jdbcTemplate;

    public OutboxMetrics(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("mall.outbox.pending", this::countPending)
                .description("outbox 中待投递（status=0）的事件条数；持续增长说明 relay 投递受阻")
                .register(registry);
    }

    private double countPending() {
        try {
            Long pending = jdbcTemplate.queryForObject(
                    "select count(*) from outbox where status=0", Long.class);
            return pending == null ? 0d : pending.doubleValue();
        } catch (Exception e) {
            // 指标采集不能反过来影响业务：查询失败时返回 NaN 让该采样点缺失，
            // 而不是把异常抛给 scrape 流程
            log.warn("[metrics] 统计 outbox 积压失败: {}", e.getMessage());
            return Double.NaN;
        }
    }
}
