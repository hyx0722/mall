package com.mall.common.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布确认回调的路由守卫测试。
 *
 * 为什么需要它：回调装配里有一处**靠约定维持、改错不会报错**的地方——
 * {@code ConfirmCallback} 拿得到 {@code CorrelationData}，但 {@code ReturnsCallback}
 * **拿不到**，只能从被退回消息的 {@code MessageProperties.messageId} 反查 outbox 行号。
 * 两个 id 都写着「outbox 行 id」，看着像冗余，很容易被后来者「简化」成统一用
 * CorrelationData —— 那样退回回调会永远拿不到行号，表现为**消息被静默退回、状态不推进**，
 * 而确认回调那半边仍然工作，问题只在「无法路由」这一条罕见路径上暴露，极难发现。
 *
 * 同时守住「拿不到行号时不能抛异常」：回调跑在 RabbitMQ 的连接线程上，
 * 抛出去只会污染日志，无法回传给任何调用方。
 *
 * 与 OutboxConfigTest 同样只用桩依赖，不连 broker，因此任何机器上都能跑。
 */
class OutboxConfirmInstallerTest {

    private RabbitTemplate rabbitTemplate;
    private OutboxService outboxService;
    private ConfirmCallbackCaptor captor;

    /** 捕获装配进去的两个回调，供各用例直接触发 */
    private static final class ConfirmCallbackCaptor {
        RabbitTemplate.ConfirmCallback confirm;
        RabbitTemplate.ReturnsCallback returns;
    }

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        outboxService = mock(OutboxService.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> noRegistry = mock(ObjectProvider.class);
        when(noRegistry.getIfAvailable()).thenReturn(null);   // 无 actuator：指标降级为「只有日志」

        captor = new ConfirmCallbackCaptor();
        // 装配动作发生在 afterSingletonsInstantiated，用 doAnswer 在设置回调时截获
        org.mockito.Mockito.doAnswer(inv -> {
            captor.confirm = inv.getArgument(0);
            return null;
        }).when(rabbitTemplate).setConfirmCallback(any());
        org.mockito.Mockito.doAnswer(inv -> {
            captor.returns = inv.getArgument(0);
            return null;
        }).when(rabbitTemplate).setReturnsCallback(any());

        new OutboxConfirmInstaller(rabbitTemplate, outboxService, noRegistry).afterSingletonsInstantiated();
    }

    @Test
    @DisplayName("ack 确认送达 -> 置已发送；nack -> 走「保持待发送 + 计数」而非置已发送")
    void confirmRoutesByAckFlag() {
        captor.confirm.confirm(new CorrelationData("42"), true, null);
        verify(outboxService).markDelivered(42L);

        captor.confirm.confirm(new CorrelationData("43"), false, "queue full");
        verify(outboxService).markRejected(eq(43L), anyString());
        verify(outboxService, never()).markDelivered(43L);
    }

    @Test
    @DisplayName("退回回调靠 messageId 反查行号（ReturnsCallback 拿不到 CorrelationData）")
    void returnedMessageResolvesRowIdFromMessageId() {
        captor.returns.returnedMessage(returnedWithMessageId("77"));

        // 行号必须来自 messageId；若被改成依赖 CorrelationData，这里会退化成 markUnroutable(null, ...)
        verify(outboxService).markUnroutable(eq(77L), anyString());
        verify(outboxService, never()).markDelivered(any());
    }

    @Test
    @DisplayName("拿不到行号时只记日志，不抛异常也不误动状态")
    void unresolvableRowIdIsSwallowed() {
        assertDoesNotThrow(() -> captor.confirm.confirm(null, true, null));
        assertDoesNotThrow(() -> captor.confirm.confirm(new CorrelationData("not-a-number"), true, null));
        assertDoesNotThrow(() -> captor.returns.returnedMessage(returnedWithMessageId(null)));

        verify(outboxService, never()).markDelivered(any());
        verify(outboxService, never()).markRejected(any(), anyString());
        verify(outboxService, never()).markUnroutable(any(), anyString());
    }

    private static ReturnedMessage returnedWithMessageId(String messageId) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (messageId != null) {
            props.setMessageId(messageId);
        }
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), props);
        return new ReturnedMessage(message, 312, "NO_ROUTE", "mall.order.exchange", "order.created");
    }
}
