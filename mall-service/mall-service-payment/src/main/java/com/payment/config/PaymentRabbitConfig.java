package com.payment.config;

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
 * 支付侧 Rabbit 配置：
 *  - JSON 消息转换器（与 order/inventory 同款，否则对端解析失败）；
 *  - 声明与 order 同源的 durable topic exchange（防 order 未启动时 publish 到不存在的交换机）；
 *  - 声明消费 order.canceled 的队列：订单取消后关闭该订单未付支付单。
 *  - 入站队列带死信（x-dead-letter-exchange=mall.order.dlx），消费经有界重试耗尽后落 q.pay.dlq。
 */
@EnableRabbit
@Configuration
public class PaymentRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_PAY_SUCCESS = RabbitTopology.RK_PAY_SUCCESS;
    public static final String RK_ORDER_CANCELED = RabbitTopology.RK_ORDER_CANCELED;
    public static final String Q_ORDER_CANCELED = RabbitTopology.Q_PAY_ORDER_CANCELED;

    public static final String DLX_EXCHANGE = RabbitTopology.DLX_EXCHANGE;
    public static final String Q_PAY_DLQ = RabbitTopology.Q_PAY_DLQ;

    private Queue withDlqArgs(String name) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        return new Queue(name, true, false, false, args);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // 订单取消：关闭未付支付单（消费失败死信）
    @Bean
    public Queue orderCanceledQueue() {
        return withDlqArgs(Q_ORDER_CANCELED);
    }

    @Bean
    public Binding bindOrderCanceled() {
        return BindingBuilder.bind(orderCanceledQueue()).to(orderExchange()).with(RK_ORDER_CANCELED);
    }

    // ---------- 死信交换机 / 死信队列 ----------

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue payDlq() {
        return new Queue(Q_PAY_DLQ, true);
    }

    @Bean
    public Binding bindPayDlq() {
        return BindingBuilder.bind(payDlq()).to(dlxExchange()).with("#");
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
