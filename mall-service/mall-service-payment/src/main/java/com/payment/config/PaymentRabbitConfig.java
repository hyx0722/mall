package com.payment.config;

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

/**
 * 支付侧 Rabbit 配置：
 *  - JSON 消息转换器（与 order/inventory 同款，否则对端解析失败）；
 *  - 声明与 order 同源的 durable topic exchange（防 order 未启动时 publish 到不存在的交换机）；
 *  - 声明消费 order.canceled 的队列：订单取消后关闭该订单未付支付单。
 */
@EnableRabbit
@Configuration
public class PaymentRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_PAY_SUCCESS = RabbitTopology.RK_PAY_SUCCESS;
    public static final String RK_ORDER_CANCELED = RabbitTopology.RK_ORDER_CANCELED;
    public static final String Q_ORDER_CANCELED = RabbitTopology.Q_PAY_ORDER_CANCELED;

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // 订单取消：关闭未付支付单
    @Bean
    public Queue orderCanceledQueue() {
        return new Queue(Q_ORDER_CANCELED, true);
    }

    @Bean
    public Binding bindOrderCanceled() {
        return BindingBuilder.bind(orderCanceledQueue()).to(orderExchange()).with(RK_ORDER_CANCELED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
