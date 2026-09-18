package com.mall.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 把发布确认回调装到（Spring Boot 自动配置出来的那个）RabbitTemplate 上。
 *
 * ── 为什么是「装上」而不是「自己定义一个 RabbitTemplate Bean」──────────────
 * 全仓没有自定义 RabbitTemplate，各服务用的是 Boot 自动配置的实例，它上面已经挂好了
 * 各服务 RabbitConfig 提供的 Jackson2JsonMessageConverter 等设置。若在本类里另定义
 * 一个 RabbitTemplate Bean，会因 @ConditionalOnMissingBean 顶掉自动配置那个，
 * 就得把 Boot 的那套配置全部复刻一遍，漏一样就是难查的行为漂移。故只做「附加回调」。
 *
 * ── 为什么用 SmartInitializingSingleton 而不是 BeanPostProcessor ───────────
 * BeanPostProcessor 实例化得极早，不该依赖 DataSource 等普通 Bean（会引发
 * 「not eligible for getting processed by all BeanPostProcessors」警告甚至提前初始化）。
 * SmartInitializingSingleton 在所有单例就绪后回调，注入什么都安全。
 *
 * ── 回调要解决的问题 ────────────────────────────────────────────────────
 * 此前 relay 用三参 {@code rabbitTemplate.send()}，没有 CorrelationData、没有 messageId、
 * 也没有 mandatory，于是「消息到了交换机但没有任何队列可路由」时 send 正常返回、
 * relay 的 try/catch 不命中，事件**静默丢失**（docs/operations.md 自认的缺陷）。
 * 现在：mandatory 命中会触发 ReturnsCallback，broker 拒绝会触发 ConfirmCallback，
 * 两者都能反查回 outbox 行并据此推进状态。
 *
 * @see OutboxServiceImpl 状态推进的具体语义与「两种失败区别对待」的理由
 */
@Slf4j
public class OutboxConfirmInstaller implements SmartInitializingSingleton {

    private static final String UNROUTABLE_METRIC = "mall.outbox.unroutable";

    private final RabbitTemplate rabbitTemplate;
    private final OutboxService outboxService;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** registry 缺席（如单测桩上下文）时为 null，指标降级为「只有日志」 */
    private Counter unroutableCounter;

    public OutboxConfirmInstaller(RabbitTemplate rabbitTemplate, OutboxService outboxService,
                                  ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            unroutableCounter = Counter.builder(UNROUTABLE_METRIC)
                    .description("outbox 中因无法路由到任何队列而被退回的消息条数；持续增长说明"
                            + "路由键与队列绑定不匹配（消息不会丢，但重试也不会成功）")
                    .register(registry);
        }
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            Long id = parseRowId(correlationData == null ? null : correlationData.getId());
            if (id == null) {
                // 非 outbox 发出的消息（当前没有，但别静默吞掉）
                log.warn("[outbox] 收到无法关联到 outbox 行的 ack={} cause={}", ack, cause);
                return;
            }
            if (ack) {
                outboxService.markDelivered(id);
            } else {
                outboxService.markRejected(id, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            if (unroutableCounter != null) {
                unroutableCounter.increment();
            }
            // ReturnsCallback 拿不到 CorrelationData，只能靠发消息时写进 messageId 的行号反查
            Long id = parseRowId(returned.getMessage().getMessageProperties().getMessageId());
            String cause = "replyCode=" + returned.getReplyCode()
                    + " replyText=" + returned.getReplyText()
                    + " exchange=" + returned.getExchange()
                    + " routingKey=" + returned.getRoutingKey();
            if (id == null) {
                log.error("[outbox] 消息被退回但无法反查 outbox 行: {}", cause);
                return;
            }
            outboxService.markUnroutable(id, cause);
        });

        log.info("[outbox] 已装配发布确认回调 confirms={} returns={} 指标={}",
                publisherConfirmsEnabled(), returnsConfigured(), unroutableCounter != null);
    }

    /** 反查 outbox 行号：解析失败返回 null，调用方据此降级为「只记日志，不动状态」 */
    private Long parseRowId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean publisherConfirmsEnabled() {
        return rabbitTemplate.getConnectionFactory() instanceof CachingConnectionFactory ccf
                && ccf.isPublisherConfirms();
    }

    /** mandatory 是 ReturnsCallback 能触发的前提，配置在 spring.rabbitmq.template.mandatory */
    private boolean returnsConfigured() {
        return rabbitTemplate.getConnectionFactory() instanceof CachingConnectionFactory ccf
                && ccf.isPublisherReturns();
    }
}
