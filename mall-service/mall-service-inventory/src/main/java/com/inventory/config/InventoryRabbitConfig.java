package com.inventory.config;

import com.mall.common.rabbit.RabbitTopology;
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
 * 拓扑常量统一定义于 {@link RabbitTopology}，避免 order/inventory 两端漂移。
 */
@Configuration
public class InventoryRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_ORDER_CREATED = RabbitTopology.RK_ORDER_CREATED;
    public static final String RK_DEDUCTED = RabbitTopology.RK_DEDUCTED;
    public static final String RK_DEDUCT_FAILED = RabbitTopology.RK_DEDUCT_FAILED;

    public static final String Q_ORDER_CREATED = RabbitTopology.Q_ORDER_CREATED;

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
