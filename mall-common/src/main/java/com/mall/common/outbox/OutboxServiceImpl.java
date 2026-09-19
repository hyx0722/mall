package com.mall.common.outbox;

import com.mall.common.metrics.OutboxMeters;
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

    // 无法路由：累加计数（已放弃的行不动，status<>3 避免重复处理）
    //
    // 为什么拆成两条而不是原来那条 CASE UPDATE：单条 CASE 无法告诉调用方
    // 「本次是否就是翻转到 3 的那一次」，于是 mall.outbox.abandoned 只能靠猜（比如每次调用都计数，
    // 会因重复回调而虚增）。拆开后第二条 UPDATE 的返回值恰好就是「本次发生了 0->3 翻转」，
    // 计数因此精确；且它带 status=0 条件，重复调用返回 0，天然幂等。
    private static final String MARK_UNROUTABLE_BUMP_SQL =
            "update outbox set retry_count = retry_count + 1, sent_time = null where id=? and status<>3";

    private static final String MARK_UNROUTABLE_ABANDON_SQL =
            "update outbox set status = 3 where id=? and status = 0 and retry_count >= ?";

    // 管理端：查看已放弃的行。不选 payload（见 AbandonedOutbox 的说明）
    private static final String SELECT_ABANDONED_SQL =
            "select id, exchange, routing_key, retry_count, created_time from outbox "
                    + "where status = 3 order by id limit ?";

    // 管理端：重投已放弃的行。status=3 条件保证幂等（重复调用影响 0 行）；
    // MySQL 允许单表 UPDATE 带 ORDER BY / LIMIT。
    // 刻意不改 created_time：保留原始入箱时间，便于判断这批事件搁置了多久。
    // relayPending 只捞 status=0，故翻回 0 后下一轮 relay 即会领取。
    private static final String REQUEUE_ABANDONED_SQL =
            "update outbox set status = 0, retry_count = 0, sent_time = null "
                    + "where status = 3 order by id limit ?";

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxMeters outboxMeters;

    public OutboxServiceImpl(DataSource dataSource, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                             OutboxMeters outboxMeters) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.outboxMeters = outboxMeters;
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
        // 只在真的把一行从 0 翻到 1 时计数：发布确认是 correlated 模式，重复 ack 属正常现象，
        // 每次回调都计数会让 delivered 虚增，失去与 pending 对照的意义
        if (jdbcTemplate.update(MARK_SENT_SQL, id) == 1) {
            outboxMeters.delivered();
        }
    }

    @Override
    public void markRejected(Long id, String cause) {
        // nack：可能是暂时性的（broker 内部错误/队列满），保持待发送并无限重试
        jdbcTemplate.update(MARK_RETRIED_SQL, id);
        // 无需判断返回值：MARK_RETRIED_SQL 没有 status 条件，命中即计数
        outboxMeters.nack();
        log.warn("[outbox] broker 拒绝(nack) id={} cause={}，保持待发送留待重投", id, cause);
    }

    @Override
    public List<AbandonedOutbox> listAbandoned(int limit) {
        return jdbcTemplate.query(SELECT_ABANDONED_SQL, (rs, rowNum) -> new AbandonedOutbox(
                rs.getLong("id"),
                rs.getString("exchange"),
                rs.getString("routing_key"),
                rs.getInt("retry_count"),
                rs.getTimestamp("created_time").toLocalDateTime()
        ), limit);
    }

    @Override
    public int requeueAbandoned(int limit) {
        int requeued = jdbcTemplate.update(REQUEUE_ABANDONED_SQL, limit);
        if (requeued > 0) {
            log.warn("[outbox] 管理端重投已放弃事件 {} 条，下一轮 relay 将领取", requeued);
        }
        return requeued;
    }

    @Override
    @Transactional
    public void markUnroutable(Long id, String cause) {
        // 无法路由：路由配置错误，重试不会成功，故有上限地放弃。
        // 两条语句同批执行，避免「计数已加、放弃未置」的中间态被旁人看到。
        jdbcTemplate.update(MARK_UNROUTABLE_BUMP_SQL, id);
        // 返回 1 表示本次调用恰好完成了 0->3 的翻转——这才是「放弃了一条」的准确时刻
        if (jdbcTemplate.update(MARK_UNROUTABLE_ABANDON_SQL, id, MAX_UNROUTABLE_RETRIES) == 1) {
            outboxMeters.abandoned();
            log.error("[outbox] 消息无法路由 id={} cause={}，已达 {} 次上限，置为 3-已放弃；"
                            + "修好路由键与队列绑定后调 POST /admin/outbox/requeue 重投",
                    id, cause, MAX_UNROUTABLE_RETRIES);
        } else {
            log.error("[outbox] 消息无法路由 id={} cause={}，重试计数已累加（上限 {}），将继续重试",
                    id, cause, MAX_UNROUTABLE_RETRIES);
        }
    }
}
