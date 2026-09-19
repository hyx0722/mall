package com.mall.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * outbox 生命周期计数的持有者：投递成功 / broker 拒绝 / 达上限放弃。
 *
 * 与 {@link OutboxMetrics} 的分工是刻意的，别合并：
 * <ul>
 *   <li>本类持有 <b>Counter</b>——需要应用在事件发生时主动 {@code increment()}，
 *       所以必须拿得到句柄。</li>
 *   <li>{@code OutboxMetrics} 是 <b>MeterBinder</b>——由 Boot 自动绑定，绑定后应用
 *       <b>拿不到句柄</b>，只能被动轮询数据库注册 Gauge。</li>
 * </ul>
 * 一个类没法同时扮演这两个角色：若让本类也实现 MeterBinder，就得同时被注册成 Bean
 * 和注入到业务侧，会把 {@code MeterRegistry} 直接拖进 {@code OutboxConfig} 的构造依赖里，
 * 从而破坏 {@code OutboxConfigTest}（该测试用桩依赖起最小上下文，不起 actuator 自动配置）。
 *
 * ── 为什么 registry 允许为 null ─────────────────────────────────────────
 * 它由 actuator 提供，而单测/裁剪过的上下文里可能没有。此时三个 Counter 保持 null，
 * 三个自增方法各自判空后**静默跳过**，指标降级为「只有日志」而不是让装配失败。
 * 这与 {@code OutboxConfirmInstaller} 的降级策略一致。
 */
public class OutboxMeters {

    /** 可为 null：为 null 表示当前上下文没有 MeterRegistry，全部计数降级为 no-op */
    private final Counter delivered;
    private final Counter nack;
    private final Counter abandoned;

    public OutboxMeters(MeterRegistry registry) {
        if (registry == null) {
            this.delivered = null;
            this.nack = null;
            this.abandoned = null;
            return;
        }
        this.delivered = Counter.builder("mall.outbox.delivered")
                .description("broker 已确认接收（ack）的 outbox 事件条数；"
                        + "与 mall.outbox.pending 对照可判断 relay 是否真的在推进")
                .register(registry);
        this.nack = Counter.builder("mall.outbox.nack")
                .description("被 broker 拒绝（nack）的 outbox 投递次数；持续增长说明 broker/队列侧有问题，"
                        + "此类失败无限重试，故与 pending 一起看")
                .register(registry);
        this.abandoned = Counter.builder("mall.outbox.abandoned")
                .description("累计被放弃（status=3）的 outbox 事件条数；出现即说明有消息因路由键与队列绑定"
                        + "不匹配而永久搁置，修好绑定后调 POST /admin/outbox/requeue 重投")
                .register(registry);
    }

    /** 仅在该次 ack 真的把一行从 0 翻到 1 时才应调用（重复 ack 是正常的，不能虚增） */
    public void delivered() {
        if (delivered != null) {
            delivered.increment();
        }
    }

    /** broker nack，行保持待发送留待重投 */
    public void nack() {
        if (nack != null) {
            nack.increment();
        }
    }

    /** 仅在该次调用真的把一行从 0 翻到 3 时才应调用 */
    public void abandoned() {
        if (abandoned != null) {
            abandoned.increment();
        }
    }
}
