package com.order.config;

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
 * 订单事件交换机/队列声明。
 * exchange: mall.order.exchange (topic)
 *   order 发布 order.created -> inventory 消费
 *   order 发布 order.canceled -> inventory 释放锁定 / payment 关闭未付支付单
 *   inventory 回执 inventory.deducted / inventory.deduct_failed -> order 消费
 *   payment 回执 pay.success -> order 消费（支付成功：待付款 -> 待发货）
 * 拓扑常量统一定义于 {@link RabbitTopology}，避免 order/inventory/payment 三端漂移。
 */
@Configuration
public class OrderRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_ORDER_CREATED = RabbitTopology.RK_ORDER_CREATED;
    public static final String RK_ORDER_CANCELED = RabbitTopology.RK_ORDER_CANCELED;
    public static final String RK_DEDUCTED = RabbitTopology.RK_DEDUCTED;
    public static final String RK_DEDUCT_FAILED = RabbitTopology.RK_DEDUCT_FAILED;
    public static final String RK_PAY_SUCCESS = RabbitTopology.RK_PAY_SUCCESS;

    public static final String Q_DEDUCTED = RabbitTopology.Q_DEDUCTED;
    public static final String Q_DEDUCT_FAILED = RabbitTopology.Q_DEDUCT_FAILED;
    public static final String Q_PAY_SUCCESS = RabbitTopology.Q_PAY_SUCCESS;

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // 扣减成功回执：order 侧消费 -> 订单置为待发货
    @Bean
    public Queue deductedQueue() {
        return new Queue(Q_DEDUCTED, true);
    }

    // 扣减失败回执：order 侧消费 -> 订单取消
    @Bean
    public Queue deductFailedQueue() {
        return new Queue(Q_DEDUCT_FAILED, true);
    }

    @Bean
    public Binding bindDeducted() {
        return BindingBuilder.bind(deductedQueue()).to(orderExchange()).with(RK_DEDUCTED);
    }

    @Bean
    public Binding bindDeductFailed() {
        return BindingBuilder.bind(deductFailedQueue()).to(orderExchange()).with(RK_DEDUCT_FAILED);
    }

    // 支付成功回执：payment 侧消费后 order 侧把订单置为待发货
    @Bean
    public Queue paySuccessQueue() {
        return new Queue(Q_PAY_SUCCESS, true);
    }

    @Bean
    public Binding bindPaySuccess() {
        return BindingBuilder.bind(paySuccessQueue()).to(orderExchange()).with(RK_PAY_SUCCESS);
    }

    // 事件统一以 JSON 收发
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
