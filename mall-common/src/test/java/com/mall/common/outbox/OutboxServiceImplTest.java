package com.mall.common.outbox;

import com.mall.common.metrics.OutboxMeters;
import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住管理端重投的两个不变量：**只碰已放弃的行**、**重复调用幂等**。
 *
 * 这两条都由 SQL 语义保证（{@code where status=3} 与 {@code UPDATE ... ORDER BY ... LIMIT}），
 * 而不是 Java 侧的判断——所以改成 H2 之类的内存库验证就没有意义了（它对这些语法的支持
 * 与 MySQL 不同），必须用真实 MySQL，做法与 InventoryMapperConcurrencyTest 一致。
 *
 * 为什么值得守：这个入口是运维在「消息因路由绑定错误被永久放弃」后唯一的恢复手段。
 * 一旦它不再幂等（比如条件从 status=3 被改成 status<>1），运维重试一次就会把同一批事件
 * 重复投递出去；一旦它越界碰到 status=0 的行，就会把正在投递中的事件回炉，同样造成重复。
 *
 * **需要本机 Docker 守护进程**。与 InventoryMapperConcurrencyTest 的差别在这里：
 * 那个是无条件失败，本类用 {@code disabledWithoutDocker} 在无 Docker 时**跳过**——
 * 因为 mall-common 原有的三个测试刻意都是零基础设施的（任何机器上都能跑），
 * 不该因为新增一个测试就把本模块的 mvn test 变成必须装 Docker。
 * CI（自带 Docker）里它会正常执行。
 */
@Testcontainers(disabledWithoutDocker = true)
class OutboxServiceImplTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mall_outbox_test")
            .withUsername("mall")
            .withPassword("mall")
            .withInitScript("outbox-schema.sql");

    private JdbcTemplate jdbc;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(MYSQL.getJdbcUrl());
        ds.setUser(MYSQL.getUsername());
        ds.setPassword(MYSQL.getPassword());
        DataSource dataSource = ds;

        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("truncate table outbox");

        // listAbandoned / requeueAbandoned 都不碰 RabbitTemplate 与 ObjectMapper，
        // 所以这里给个不带连接工厂的空实例即可，不必起 broker。
        // OutboxMeters 传 null registry：顺带覆盖「无 actuator 时降级为 no-op」那条路径。
        outboxService = new OutboxServiceImpl(dataSource, new RabbitTemplate(),
                JsonMapper.builder().build(), new OutboxMeters(null));
    }

    /** 插一行并直接指定 status/retry_count，省去走完整投递链路 */
    private long seed(String routingKey, int status, int retryCount) {
        jdbc.update("insert into outbox(exchange,routing_key,payload,status,retry_count,delay_ms,created_time) "
                + "values('mall.order.exchange',?,?,?,?,null,now())", routingKey, "{}", status, retryCount);
        return jdbc.queryForObject("select max(id) from outbox", Long.class);
    }

    private int statusOf(long id) {
        return jdbc.queryForObject("select status from outbox where id=?", Integer.class, id);
    }

    private int retryCountOf(long id) {
        return jdbc.queryForObject("select retry_count from outbox where id=?", Integer.class, id);
    }

    @Test
    @DisplayName("重投把 3→0 并清零计数，且重复调用不再生效")
    void requeueIsIdempotent() {
        long abandoned = seed("no.such.binding", 3, 20);

        assertEquals(1, outboxService.requeueAbandoned(50), "首次重投应命中 1 行");
        assertEquals(0, statusOf(abandoned), "重投后应回到待发送");
        assertEquals(0, retryCountOf(abandoned), "重投必须清零计数，否则下一轮立刻又被放弃");

        assertEquals(0, outboxService.requeueAbandoned(50),
                "再次调用必须返回 0——幂等性是运维敢反复点这个按钮的前提");
    }

    @Test
    @DisplayName("绝不碰活跃行：待发送与已发送的不受重投影响")
    void requeueNeverTouchesLiveRows() {
        long pending = seed("order.created", 0, 0);
        long sent = seed("order.created", 1, 0);
        long abandoned = seed("no.such.binding", 3, 20);

        assertEquals(1, outboxService.requeueAbandoned(50), "只有那 1 行已放弃的该被重投");

        assertEquals(0, statusOf(pending), "待发送行的状态不该被改动");
        assertEquals(1, statusOf(sent), "已发送行不该被回炉，否则会重复投递");
        assertEquals(0, statusOf(abandoned));
    }

    @Test
    @DisplayName("重投有上限：一次调用不能把整个积压翻过来冲垮 broker")
    void requeueRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            seed("no.such.binding", 3, 20);
        }

        assertEquals(2, outboxService.requeueAbandoned(2), "应只重投 limit 指定的条数");
        assertEquals(3L, jdbc.queryForObject("select count(*) from outbox where status=3", Long.class),
                "剩余 3 行应仍是已放弃，留待下次调用");
    }

    @Test
    @DisplayName("listAbandoned 只列已放弃的行，且不带 payload")
    void listAbandonedFiltersAndOmitsPayload() {
        seed("order.created", 0, 0);
        seed("order.created", 1, 0);
        long abandoned = seed("no.such.binding", 3, 20);

        List<AbandonedOutbox> rows = outboxService.listAbandoned(50);

        assertEquals(1, rows.size(), "只应看到已放弃的那一行");
        assertEquals(abandoned, rows.get(0).id());
        // routingKey 是排查根因的关键字段：运维要拿它去核对队列绑定
        assertEquals("no.such.binding", rows.get(0).routingKey());
        assertEquals(20, rows.get(0).retryCount());
        assertTrue(rows.get(0).createdTime() != null, "应带出原始入箱时间，用于判断搁置了多久");
    }
}
