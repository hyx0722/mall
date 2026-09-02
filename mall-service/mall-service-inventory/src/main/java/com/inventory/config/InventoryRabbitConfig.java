package com.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit

/**
 * 库存侧 RabbitMQ 声明：绑定 order.created 队列，
 * 并向 mall.order.exchange 回执扣减结果（deducted / deduct_failed）。
 */
@Configuration
public class InventoryRabbitConfig {

    public static final String ORDER_EXCHANGE = "mall.order.exchange";
    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_DEDUCTED = "inventory.deducted";
    public static final String RK_DEDUCT_FAILED = "inventory.deduct_failed";

    public static final String Q_ORDER_CREATED = "q.inventory.order.created";

    @Bean
    public TopicExchange inventoryOrderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(Q_ORDER_CREATED, true);
    }

    @Bean
    public Binding bindOrderCreated() {
        return BindingBuilder.bind(orderCreatedQueue()).to(inventoryOrderExchange()).with(RK_ORDER_CREATED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
