package com.mall.common.outbox;

import com.model.bean.Outbox;
// Spring Boot 4 的托管 Mapper 是 Jackson 3（tools.jackson），不再是 Jackson 2（com.fasterxml）
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
 */
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private static final int BATCH = 50;

    private static final String INSERT_SQL =
            "insert into outbox(exchange,routing_key,payload,status,retry_count,delay_ms,created_time) "
                    + "values(?,?,?,0,0,?,now())";

    // for update skip locked：多实例并发 relay 时各领各的，须在事务内执行
    private static final String SELECT_PENDING_SQL =
            "select id,exchange,routing_key,payload,delay_ms from outbox "
                    + "where status=0 order by id limit ? for update skip locked";

    private static final String MARK_SENT_SQL =
            "update outbox set status=1, sent_time=now() where id=? and status=0";

    private static final String MARK_RETRIED_SQL =
            "update outbox set retry_count=retry_count+1 where id=?";

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
        for (Outbox row : pending) {
            try {
                send(row);
                jdbcTemplate.update(MARK_SENT_SQL, row.getId());
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
        if (row.getDelayMs() != null && row.getDelayMs() > 0) {
            // 延迟消息：per-message TTL，Rabbit 到点后由持有队列死信到目标交换机
            props.setExpiration(String.valueOf(row.getDelayMs()));
        }
        Message message = new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), props);
        rabbitTemplate.send(row.getExchange(), row.getRoutingKey(), message);
        log.debug("[outbox] 已投递 id={} -> {} / {}", row.getId(), row.getExchange(), row.getRoutingKey());
    }
}
