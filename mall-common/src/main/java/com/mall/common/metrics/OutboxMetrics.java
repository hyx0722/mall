package com.mall.common.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 事务发件箱积压指标（注册两条 Gauge，都是直接查表得到的「此刻状态」）：
 * <ul>
 *   <li>{@code mall.outbox.pending} = status=0（待投递）的行数；</li>
 *   <li>{@code mall.outbox.abandoned.backlog} = status=3（已放弃）的行数。</li>
 * </ul>
 *
 * 为什么需要它们：outbox 由各服务的 relay 定时领取投递（每 3s 一批），一旦 relay 停摆或持续投递失败，
 * 事件会静静堆积在表里而**没有任何外部表征**——表现为订单不推进、库存不释放、支付状态不回写，
 * 排查时只能从业务现象倒推。有这两条指标后，积压量可直接观测，配一条阈值告警就能第一时间发现。
 *
 * 两条指标的分工：pending 涨而不降说明「投不出去」；abandoned.backlog > 0 说明「已经放弃了」——
 * 后者重试永远不会成功（路由绑定的配置错误），必须修好绑定后调
 * {@code POST /admin/outbox/requeue} 重投，只等是等不回来的。
 *
 * 注意：只有配备 outbox 表的服务（order / payment / inventory）才能导入本类，
 * 其余服务的库里没有该表，查询会抛异常（已在 count* 内兜住，但指标会恒为 NaN）。
 *
 * 与 {@link OutboxMeters} 的分工见那个类的 javadoc——本类是 MeterBinder（被动轮询注册 Gauge），
 * 那个类持有 Counter（需要句柄主动自增），刻意没有合并。
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
        Gauge.builder("mall.outbox.abandoned.backlog", this::countAbandoned)
                .description("outbox 中已放弃（status=3）的事件条数；>0 说明有消息因路由键与队列绑定"
                        + "不匹配被永久搁置，修好绑定后调 POST /admin/outbox/requeue 重投")
                .register(registry);
    }

    private double countPending() {
        return countByStatus("status=0", "待投递");
    }

    private double countAbandoned() {
        return countByStatus("status=3", "已放弃");
    }

    private double countByStatus(String statusCondition, String label) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from outbox where " + statusCondition, Long.class);
            return count == null ? 0d : count.doubleValue();
        } catch (Exception e) {
            // 指标采集不能反过来影响业务：查询失败时返回 NaN 让该采样点缺失，
            // 而不是把异常抛给 scrape 流程
            log.warn("[metrics] 统计 outbox {} 失败: {}", label, e.getMessage());
            return Double.NaN;
        }
    }
}
