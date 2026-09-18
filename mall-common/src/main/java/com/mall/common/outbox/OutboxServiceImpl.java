package com.mall.common.outbox;

import com.model.bean.Outbox;
// Spring Boot 4 的托管 Mapper 是 Jackson 3（tools.jackson），不再是 Jackson 2（com.fasterxml）
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 事务性发件箱实现（order / payment / inventory 三服务共用）。
 *
 * 为什么用 JdbcTemplate 而不是 MyBatis Mapper：
 * 本类在 mall-common 里，而各服务的 {@code @SpringBootApplication} 只扫自己的包，
 * 放在这里的 {@code @Mapper} 接口不会被自动扫描。若为了它给各服务加
 * {@code @MapperScan}，会触发 MyBatis-Plus 的经典陷阱——一旦容器里存在
 * {@code MapperScannerConfigurer}，{@code AutoConfiguredMapperScannerRegistrar}
 * 便整体退避，**各服务自己原有的 mapper 全部停止注册**，且只在启动期以
 * 「找不到 bean」的形式炸出来。改用 JdbcTemplate 可完全绕开这一层，
 * 且本仓库的 {@code OutboxMetrics} 早已用同样方式读写 outbox 表。
 *
 * SQL 与三张同名表的 DDL（order.sql / payment.sql / inventory.sql）保持一致。
 *
 * ── 发布确认（publisher confirms / returns）──────────────────────────────
 * 投递状态由 {@link OutboxConfirmInstaller} 装上来的 ConfirmCallback / ReturnsCallback 推进，
 * 两种失败的处理**刻意不同**：
 * <ul>
 *   <li><b>发送抛异常</b>（连不上 broker）与 <b>nack</b>（broker 拒绝）→ 只累加重试计数、保持
 *       待发送，**无限重试**。这类失败通常是暂时的，而 outbox 存在的意义正是「broker 迟早会回来」；
 *       给它加上限会导致一次 broker 重启就永久丢事件。</li>
 *   <li><b>消息被退回</b>（mandatory 命中，交换机没有任何队列可路由）→ 累加计数，超过
 *       {@link #MAX_UNROUTABLE_RETRIES} 置 {@code status=3 已放弃}。这类失败是**路由配置错误**，
 *       重试永远不会成功，只能靠指标告警 + 人工介入。</li>
 * </ul>
 */
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private static final int BATCH = 50;

    /** 无法路由的重试上限；超过即置 3-已放弃。仅用于「路由不到队列」，不用于连接异常/nack */
    private static final int MAX_UNROUTABLE_RETRIES = 20;

    private static final String INSERT_SQL =
            "insert into outbox(exchange,routing_key,payload,status,retry_count,delay_ms,created_time) "
                    + "values(?,?,?,0,0,?,now())";

    // for update skip locked：多实例并发 relay 时各领各的，须在事务内执行
    private static final String SELECT_PENDING_SQL =
            "select id,exchange,routing_key,payload,delay_ms from outbox "
                    + "where status=0 order by id limit ? for update skip locked";

    // 置已发送：带 status=0 条件，收到重复 ack 时天然幂等
    private static final String MARK_SENT_SQL =
            "update outbox set status=1, sent_time=now() where id=? and status=0";

    // 连接异常 / nack：只累加计数，status 保持 0 留待下轮（无限重试，见类注释）
    private static final String MARK_RETRIED_SQL =
            "update outbox set retry_count=retry_count+1 where id=?";

    // 无法路由：累加计数，达上限置 3-已放弃。status<>3 避免已放弃的行被重复处理
    private static final String MARK_UNROUTABLE_SQL =
            "update outbox set status = case when retry_count + 1 >= ? then 3 else 0 end, "
                    + "retry_count = retry_count + 1, sent_time = null where id=? and status<>3";

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(DataSource dataSource, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(String exchange, String routingKey, Long delayMs, Object payload) {
        doEnqueue(exchange, routingKey, delayMs, payload);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueNewTx(String exchange, String routingKey, Long delayMs, Object payload) {
        doEnqueue(exchange, routingKey, delayMs, payload);
    }

    private void doEnqueue(String exchange, String routingKey, Long delayMs, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update(INSERT_SQL, exchange, routingKey, json, delayMs);
        } catch (Exception e) {
            // 入箱失败说明事件源不可靠，抛出让业务事务回滚（宁可业务失败也不丢事件）
            throw new IllegalStateException(
                    "[outbox] 事件入箱失败 exchange=" + exchange + " key=" + routingKey, e);
        }
    }

    @Override
    @Transactional
    public void relayPending() {
        List<Outbox> pending = jdbcTemplate.query(SELECT_PENDING_SQL,
                (rs, rowNum) -> {
                    Outbox row = new Outbox();
                    row.setId(rs.getLong("id"));
                    row.setExchange(rs.getString("exchange"));
                    row.setRoutingKey(rs.getString("routing_key"));
                    row.setPayload(rs.getString("payload"));
                    row.setDelayMs(rs.getObject("delay_ms", Long.class));
                    return row;
                }, BATCH);
        // 开着发布确认时，是否送达由 ConfirmCallback 决定，这里不能提前置已发送；
        // 关着时无从得知，只能沿用历史行为乐观标记（否则该行会每 3s 被无限重投）
        boolean confirmDriven = publisherConfirmsEnabled();
        for (Outbox row : pending) {
            try {
                send(row);
                if (!confirmDriven) {
                    jdbcTemplate.update(MARK_SENT_SQL, row.getId());
                }
            } catch (Exception e) {
                // 单行失败不影响其它行；置重试计数后留待下轮（status 仍为 0）
                jdbcTemplate.update(MARK_RETRIED_SQL, row.getId());
                log.error("[outbox] 投递失败 id={} exchange={} key={}",
                        row.getId(), row.getExchange(), row.getRoutingKey(), e);
            }
        }
    }

    private void send(Outbox row) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        // messageId 与 CorrelationData 都存 outbox 行 id：
        // ConfirmCallback 拿得到 CorrelationData，但 ReturnsCallback **拿不到**，
        // 只能从被退回消息的 MessageProperties.messageId 反查行号。
        String rowId = String.valueOf(row.getId());
        props.setMessageId(rowId);
        if (row.getDelayMs() != null && row.getDelayMs() > 0) {
            // 延迟消息：per-message TTL，Rabbit 到点后由持有队列死信到目标交换机
            props.setExpiration(String.valueOf(row.getDelayMs()));
        }
        Message message = new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), props);
        CorrelationData correlationData = new CorrelationData(rowId);
        rabbitTemplate.send(row.getExchange(), row.getRoutingKey(), message, correlationData);
        log.debug("[outbox] 已投递 id={} -> {} / {}", row.getId(), row.getExchange(), row.getRoutingKey());
    }

    /**
     * 是否已开启发布确认。关着时 relay 必须乐观置已发送——否则没有任何回调会推进状态，
     * 该行会被每轮 relay 无限重投。
     *
     * ConnectionFactory 可能为 null（如单测里的桩），此时按「未开启」处理。
     */
    private boolean publisherConfirmsEnabled() {
        return rabbitTemplate.getConnectionFactory() instanceof CachingConnectionFactory ccf
                && ccf.isPublisherConfirms();
    }

    @Override
    public void markDelivered(Long id) {
        jdbcTemplate.update(MARK_SENT_SQL, id);
    }

    @Override
    public void markRejected(Long id, String cause) {
        // nack：可能是暂时性的（broker 内部错误/队列满），保持待发送并无限重试
        jdbcTemplate.update(MARK_RETRIED_SQL, id);
        log.warn("[outbox] broker 拒绝(nack) id={} cause={}，保持待发送留待重投", id, cause);
    }

    @Override
    public void markUnroutable(Long id, String cause) {
        // 无法路由：路由配置错误，重试不会成功，故有上限地放弃
        jdbcTemplate.update(MARK_UNROUTABLE_SQL, MAX_UNROUTABLE_RETRIES, id);
        log.error("[outbox] 消息无法路由 id={} cause={}，已达 {} 次上限的将置为 3-已放弃",
                id, cause, MAX_UNROUTABLE_RETRIES);
    }
}
