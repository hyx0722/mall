package com.inventory.config;

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
 * 库存侧 RabbitMQ 声明：绑定 order.created 与 order.canceled 队列，
 * 并向 mall.order.exchange 回执扣减结果（deducted / deduct_failed）。
 * 入站队列带死信（x-dead-letter-exchange=mall.order.dlx），消费经有界重试耗尽后落 q.inventory.dlq。
 * 拓扑常量统一定义于 {@link RabbitTopology}，避免 order/inventory 两端漂移。
 */
@EnableRabbit
@Configuration
public class InventoryRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_ORDER_CREATED = RabbitTopology.RK_ORDER_CREATED;
    public static final String RK_ORDER_CANCELED = RabbitTopology.RK_ORDER_CANCELED;
    public static final String RK_DEDUCTED = RabbitTopology.RK_DEDUCTED;
    public static final String RK_DEDUCT_FAILED = RabbitTopology.RK_DEDUCT_FAILED;

    public static final String Q_ORDER_CREATED = RabbitTopology.Q_ORDER_CREATED;
    public static final String Q_ORDER_CANCELED = RabbitTopology.Q_INVENTORY_ORDER_CANCELED;

    public static final String DLX_EXCHANGE = RabbitTopology.DLX_EXCHANGE;
    public static final String Q_INVENTORY_DLQ = RabbitTopology.Q_INVENTORY_DLQ;

    private Queue withDlqArgs(String name) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        return new Queue(name, true, false, false, args);
    }

    @Bean
    public TopicExchange inventoryOrderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return withDlqArgs(Q_ORDER_CREATED);
    }

    @Bean
    public Binding bindOrderCreated() {
        return BindingBuilder.bind(orderCreatedQueue()).to(inventoryOrderExchange()).with(RK_ORDER_CREATED);
    }

    // 订单取消：释放该订单锁定的库存
    @Bean
    public Queue orderCanceledQueue() {
        return withDlqArgs(Q_ORDER_CANCELED);
    }

    @Bean
    public Binding bindOrderCanceled() {
        return BindingBuilder.bind(orderCanceledQueue()).to(inventoryOrderExchange()).with(RK_ORDER_CANCELED);
    }

    // ---------- 死信交换机 / 死信队列 ----------

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue inventoryDlq() {
        return new Queue(Q_INVENTORY_DLQ, true);
    }

    @Bean
    public Binding bindInventoryDlq() {
        return BindingBuilder.bind(inventoryDlq()).to(dlxExchange()).with("#");
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
