package com.user.config;

import com.mall.common.rabbit.RabbitTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户侧 RabbitMQ 声明：订阅订单取消 / 已退款两个事件，把该订单用掉的券退回。
 *
 * 为什么退券走事件而不是让 order 服务同步调用：
 * order 的取消路径有「每 5 分钟对账扫表」兜底，但**退款到账路径没有任何对账**——
 * 若退券是同步调用，user 服务抖一下就会让订单回滚，而 pay.refund.success 重试耗尽落 DLQ 后
 * 订单会永久卡在 5退款中。走事件则消费失败有有界重试 + DLQ，且**完全不阻塞订单状态推进**。
 *
 * 这与 inventory 释放锁定、payment 关闭未付支付单是同一批事件、同一套机制，各绑各的队列。
 * 本服务**只消费不发布**，故没有 outbox 表、不导入 OutboxConfig。
 */
@EnableRabbit
@Configuration
public class UserRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_ORDER_CANCELED = RabbitTopology.RK_ORDER_CANCELED;
    public static final String RK_ORDER_REFUNDED = RabbitTopology.RK_ORDER_REFUNDED;

    public static final String Q_USER_ORDER_CANCELED = RabbitTopology.Q_USER_ORDER_CANCELED;
    public static final String Q_USER_ORDER_REFUNDED = RabbitTopology.Q_USER_ORDER_REFUNDED;

    public static final String DLX_EXCHANGE = RabbitTopology.DLX_EXCHANGE;
    public static final String Q_USER_DLQ = RabbitTopology.Q_USER_DLQ;

    private Queue withDlqArgs(String name) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        return new Queue(name, true, false, false, args);
    }

    @Bean
    public TopicExchange userOrderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // ---------- 订单取消：把该订单用掉的券退回 ----------

    @Bean
    public Queue userOrderCanceledQueue() {
        return withDlqArgs(Q_USER_ORDER_CANCELED);
    }

    @Bean
    public Binding bindUserOrderCanceled() {
        return BindingBuilder.bind(userOrderCanceledQueue()).to(userOrderExchange()).with(RK_ORDER_CANCELED);
    }

    // ---------- 订单已退款：把该订单用掉的券退回 ----------

    @Bean
    public Queue userOrderRefundedQueue() {
        return withDlqArgs(Q_USER_ORDER_REFUNDED);
    }

    @Bean
    public Binding bindUserOrderRefunded() {
        return BindingBuilder.bind(userOrderRefundedQueue()).to(userOrderExchange()).with(RK_ORDER_REFUNDED);
    }

    // ---------- 死信交换机 / 死信队列 ----------

    @Bean
    public TopicExchange userDlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue userDlq() {
        return new Queue(Q_USER_DLQ, true);
    }

    @Bean
    public Binding bindUserDlq() {
        return BindingBuilder.bind(userDlq()).to(userDlxExchange()).with("#");
    }

    // ---------- 监听容器工厂：有界重试（3 次）耗尽后拒绝即死信 ----------

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxRetries(2)
                        .backOffOptions(1000, 2.0, 10000)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build());
        return factory;
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
