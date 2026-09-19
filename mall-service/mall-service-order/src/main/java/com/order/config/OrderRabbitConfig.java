package com.order.config;

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
 * 订单侧 RabbitMQ 声明。
 *
 * 主交换机 mall.order.exchange (topic, durable)：
 *   order 发布 order.created -> inventory 消费
 *   order 发布 order.canceled -> inventory 释放锁定 / payment 关闭未付支付单
 *   inventory 回执 inventory.deducted / inventory.deduct_failed -> order 消费
 *   payment 回执 pay.success -> order 消费（支付成功：待付款 -> 待发货）
 *   order 发布 order.shipped -> user 消费（给买家写「已发货」站内通知；
 *                                队列由 user 侧声明，本服务只声明交换机）
 *
 * 支付超时（延迟消息 DLX + per-message TTL）：
 *   mall.order.delay.exchange 承载带 TTL 的超时标记，进入无消费者持有队列 q.delay.order.timeout，
 *   TTL 到点死信回主交换机 order.timeout -> q.order.timeout（order 消费后走统一取消漏斗）。
 *
 * 死信：order 侧每个入站队列带 x-dead-letter-exchange=mall.order.dlx；消费经有界重试耗尽后
 * reject(requeue=false) 落入 q.order.dlq。
 *
 * 拓扑常量统一定义于 {@link RabbitTopology}，避免 order/inventory/payment 三端漂移。
 */
@Configuration
@EnableRabbit
public class OrderRabbitConfig {

    public static final String ORDER_EXCHANGE = RabbitTopology.ORDER_EXCHANGE;
    public static final String RK_ORDER_CREATED = RabbitTopology.RK_ORDER_CREATED;
    public static final String RK_ORDER_CANCELED = RabbitTopology.RK_ORDER_CANCELED;
    public static final String RK_DEDUCTED = RabbitTopology.RK_DEDUCTED;
    public static final String RK_DEDUCT_FAILED = RabbitTopology.RK_DEDUCT_FAILED;
    public static final String RK_PAY_SUCCESS = RabbitTopology.RK_PAY_SUCCESS;

    public static final String RK_REFUND_REQUEST = RabbitTopology.RK_REFUND_REQUEST;
    public static final String RK_PAY_REFUND_SUCCESS = RabbitTopology.RK_PAY_REFUND_SUCCESS;
    /** order 发布：订单已完成（买家确认收货）。消费者是 order 自己（结算），故队列也在本配置里声明 */
    public static final String RK_ORDER_COMPLETED = RabbitTopology.RK_ORDER_COMPLETED;
    public static final String Q_ORDER_COMPLETED = RabbitTopology.Q_ORDER_COMPLETED;
    public static final String RK_ORDER_REFUNDED = RabbitTopology.RK_ORDER_REFUNDED;
    public static final String Q_ORDER_REFUND_SUCCESS = RabbitTopology.Q_ORDER_REFUND_SUCCESS;

    /**
     * order 发布：订单已发货（最后一个卖家发货，1待发货 -> 2待收货）。
     *
     * <p>⚠️ <b>这里只导出常量，**不要**为本事件声明队列。</b>
     * 队列由消费者（user 服务的 {@code q.user.order.shipped}）自己声明，这是本仓的既有约定
     * （见 {@link RabbitTopology#RK_ORDER_COMPLETED} 的注释）。在本配置里多声明一条队列，
     * 结果是多出一条**没有监听器**的队列：消息进去只是堆积，且真正出问题时
     * 会让人误以为消费端已经在处理。
     */
    public static final String RK_ORDER_SHIPPED = RabbitTopology.RK_ORDER_SHIPPED;

    /**
     * order 发布：买家写了商品评价 / 卖家回复了评价。
     *
     * <p>同 {@link #RK_ORDER_SHIPPED}：**这里只导出常量，不要声明队列**。
     * 队列由消费者（user 服务的 {@code q.user.review.*}）自己声明。
     * 在本配置里多声明一条队列，得到的是**没有监听器**的队列——消息只堆积，
     * 而且会让人误以为消费端已经在处理。
     */
    public static final String RK_REVIEW_CREATED = RabbitTopology.RK_REVIEW_CREATED;
    public static final String RK_REVIEW_REPLIED = RabbitTopology.RK_REVIEW_REPLIED;

    public static final String DELAY_EXCHANGE = RabbitTopology.DELAY_EXCHANGE;
    public static final String RK_DELAY_ORDER_TIMEOUT = RabbitTopology.RK_DELAY_ORDER_TIMEOUT;
    public static final String Q_DELAY_ORDER_TIMEOUT = RabbitTopology.Q_DELAY_ORDER_TIMEOUT;
    public static final String RK_ORDER_TIMEOUT = RabbitTopology.RK_ORDER_TIMEOUT;
    public static final String Q_ORDER_TIMEOUT = RabbitTopology.Q_ORDER_TIMEOUT;

    public static final String DLX_EXCHANGE = RabbitTopology.DLX_EXCHANGE;
    public static final String Q_ORDER_DLQ = RabbitTopology.Q_ORDER_DLQ;

    public static final String Q_DEDUCTED = RabbitTopology.Q_DEDUCTED;
    public static final String Q_DEDUCT_FAILED = RabbitTopology.Q_DEDUCT_FAILED;
    public static final String Q_PAY_SUCCESS = RabbitTopology.Q_PAY_SUCCESS;

    /** 给入站消费队列附加「消费失败死信到统一 DLX」的队列参数 */
    private Queue withDlqArgs(String name) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        return new Queue(name, true, false, false, args);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // 扣减成功回执：order 侧消费 -> 订单置为待发货
    @Bean
    public Queue deductedQueue() {
        return withDlqArgs(Q_DEDUCTED);
    }

    // 扣减失败回执：order 侧消费 -> 订单取消
    @Bean
    public Queue deductFailedQueue() {
        return withDlqArgs(Q_DEDUCT_FAILED);
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
        return withDlqArgs(Q_PAY_SUCCESS);
    }

    @Bean
    public Binding bindPaySuccess() {
        return BindingBuilder.bind(paySuccessQueue()).to(orderExchange()).with(RK_PAY_SUCCESS);
    }

    // 退款到账回执：payment 打款成功后 order 侧把订单置 6已退款。
    // 注意 order 只声明入站队列；refund.request（order 发布、payment 消费）与
    // order.refunded（order 发布、inventory 消费）的队列由各自消费端声明。
    @Bean
    public Queue refundSuccessQueue() {
        return withDlqArgs(Q_ORDER_REFUND_SUCCESS);
    }

    @Bean
    public Binding bindRefundSuccess() {
        return BindingBuilder.bind(refundSuccessQueue()).to(orderExchange()).with(RK_PAY_REFUND_SUCCESS);
    }

    // 订单已完成：本服务自己消费生成结算明细。
    // 声明入站队列的规则是「消费者声明」，这里发布方与消费方都是 order，所以队列落在本配置里；
    // 日后若通知中心等也要订阅 order.completed，由那个服务自己声明自己的队列，改不到这里。
    @Bean
    public Queue orderCompletedQueue() {
        return withDlqArgs(Q_ORDER_COMPLETED);
    }

    @Bean
    public Binding bindOrderCompleted() {
        return BindingBuilder.bind(orderCompletedQueue()).to(orderExchange()).with(RK_ORDER_COMPLETED);
    }

    // ---------- 支付超时延迟消息 ----------

    @Bean
    public TopicExchange delayExchange() {
        return new TopicExchange(DELAY_EXCHANGE, true, false);
    }

    // 持有队列：无消费者；per-message TTL 到点后死信回主交换机 -> order.timeout
    @Bean
    public Queue delayOrderTimeoutQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", ORDER_EXCHANGE);
        args.put("x-dead-letter-routing-key", RK_ORDER_TIMEOUT);
        return new Queue(Q_DELAY_ORDER_TIMEOUT, true, false, false, args);
    }

    @Bean
    public Binding bindDelayOrderTimeout() {
        return BindingBuilder.bind(delayOrderTimeoutQueue()).to(delayExchange()).with(RK_DELAY_ORDER_TIMEOUT);
    }

    // 延迟超时标记死信后到达的消费队列（本身也带 DLQ 参数，消费失败落死信）
    @Bean
    public Queue orderTimeoutQueue() {
        return withDlqArgs(Q_ORDER_TIMEOUT);
    }

    @Bean
    public Binding bindOrderTimeout() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(orderExchange()).with(RK_ORDER_TIMEOUT);
    }

    // ---------- 死信交换机 / 死信队列 ----------

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDlq() {
        return new Queue(Q_ORDER_DLQ, true);
    }

    @Bean
    public Binding bindOrderDlq() {
        return BindingBuilder.bind(orderDlq()).to(dlxExchange()).with("#");
    }

    // ---------- 监听容器工厂：有界重试（3 次）耗尽后拒绝即死信，避免无限 requeue ----------

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(
                RetryInterceptorBuilder.stateless()
                        .maxRetries(2) // 首试后再重试 2 次 = 共 3 次
                        .backOffOptions(1000, 2.0, 10000)
                        .recoverer(new RejectAndDontRequeueRecoverer())
                        .build());
        return factory;
    }

    // 事件统一以 JSON 收发
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
