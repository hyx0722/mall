package com.mall.common.audit;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计日志的 JdbcTemplate 实现（与 outbox 同一套理由：mall-common 里的 Mapper
 * 不在各服务的组件扫描范围内，用 JdbcTemplate 可以完全绕开 MyBatis 扫描的坑）。
 *
 * <p>表在每个服务**自己的库**里各建一张：审计数据跟着业务库走，不引入新的共享库，
 * 也不把「查日志」变成跨库依赖。代价是管理后台要按服务分别查询——低流量场景下
 * 前端聚合足够，且比再造一个审计微服务轻得多。</p>
 */
@Slf4j
public class AuditLogRepository {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS `audit_log` (
              `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志ID',
              `operator_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '操作人用户ID；0=系统/未识别',
              `operator_name` VARCHAR(64)  DEFAULT NULL COMMENT '操作人用户名',
              `operator_role` TINYINT      NOT NULL DEFAULT 0 COMMENT '操作人角色：1普通用户 2管理员',
              `service`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '服务名（spring.application.name）',
              `action`        VARCHAR(100) NOT NULL DEFAULT '' COMMENT '操作动作',
              `target_type`   VARCHAR(32)  DEFAULT NULL COMMENT '对象类型：USER/PRODUCT/ORDER...',
              `target_id`     VARCHAR(64)  DEFAULT NULL COMMENT '对象ID',
              `method`        VARCHAR(8)   DEFAULT NULL COMMENT 'HTTP方法',
              `path`          VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
              `params`        VARCHAR(1000) DEFAULT NULL COMMENT '脱敏后的查询参数；不记录请求体',
              `result`        TINYINT      NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
              `error_msg`     VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
              `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
              `user_agent`    VARCHAR(255) DEFAULT NULL COMMENT 'User-Agent',
              `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
              PRIMARY KEY (`id`),
              KEY `idx_created` (`created_time`),
              KEY `idx_operator` (`operator_id`,`created_time`),
              KEY `idx_action` (`action`,`created_time`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作审计日志'
            """;

    private static final String INSERT_SQL = """
            insert into `audit_log`
              (`operator_id`,`operator_name`,`operator_role`,`service`,`action`,`target_type`,`target_id`,
               `method`,`path`,`params`,`result`,`error_msg`,`ip`,`user_agent`,`created_time`)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())
            """;

    private static final String SELECT_COLUMNS = """
            select `id`,`operator_id`,`operator_name`,`operator_role`,`service`,`action`,`target_type`,`target_id`,
                   `method`,`path`,`params`,`result`,`error_msg`,`ip`,`user_agent`,`created_time`
            from `audit_log`
            """;

    private static final RowMapper<AuditLogEntry> ROW_MAPPER = (rs, rowNum) -> {
        AuditLogEntry e = new AuditLogEntry();
        e.setId(rs.getLong("id"));
        e.setOperatorId(rs.getLong("operator_id"));
        e.setOperatorName(rs.getString("operator_name"));
        e.setOperatorRole(rs.getInt("operator_role"));
        e.setService(rs.getString("service"));
        e.setAction(rs.getString("action"));
        e.setTargetType(rs.getString("target_type"));
        e.setTargetId(rs.getString("target_id"));
        e.setMethod(rs.getString("method"));
        e.setPath(rs.getString("path"));
        e.setParams(rs.getString("params"));
        e.setResult(rs.getInt("result"));
        e.setErrorMsg(rs.getString("error_msg"));
        e.setIp(rs.getString("ip"));
        e.setUserAgent(rs.getString("user_agent"));
        e.setCreatedTime(rs.getTimestamp("created_time").toLocalDateTime());
        return e;
    };

    private final JdbcTemplate jdbcTemplate;
    private final String serviceName;

    public AuditLogRepository(DataSource dataSource, String serviceName) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.serviceName = serviceName;
    }

    /**
     * 启动时补建表：存量库不需要手工执行 DDL，也不会因为忘了建表而丢第一条审计日志。
     * 建表失败只告警，不影响服务启动（审计是旁路，不能反过来卡住交易）。
     */
    @PostConstruct
    void initSchema() {
        try {
            jdbcTemplate.execute(CREATE_TABLE_SQL);
        } catch (Exception e) {
            log.warn("[audit] 初始化 audit_log 表失败（审计日志将不可用，业务不受影响）：{}", e.getMessage());
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void insert(AuditLogEntry entry) {
        jdbcTemplate.update(INSERT_SQL,
                entry.getOperatorId() == null ? 0L : entry.getOperatorId(),
                entry.getOperatorName(),
                entry.getOperatorRole() == null ? 0 : entry.getOperatorRole(),
                serviceName,
                entry.getAction() == null ? "" : entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getMethod(),
                entry.getPath(),
                entry.getParams(),
                entry.getResult() == null ? 1 : entry.getResult(),
                entry.getErrorMsg(),
                entry.getIp(),
                entry.getUserAgent());
    }

    public long count(Long operatorId, String action, Integer result, String from, String to) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(operatorId, action, result, from, to, args);
        Long total = jdbcTemplate.queryForObject("select count(*) from `audit_log`" + where, Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<AuditLogEntry> page(Long operatorId, String action, Integer result, String from, String to,
                                    int offset, int size) {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(operatorId, action, result, from, to, args);
        args.add(size);
        args.add(offset);
        return jdbcTemplate.query(SELECT_COLUMNS + where + " order by id desc limit ? offset ?", ROW_MAPPER, args.toArray());
    }

    /**
     * 过滤条件集中在这里，count 与 page 共用同一份 WHERE，避免两边条件漂移
     * （典型症状是总数对、翻页却丢行）。
     */
    private String buildWhere(Long operatorId, String action, Integer result, String from, String to,
                              List<Object> args) {
        StringBuilder sql = new StringBuilder(" where 1=1");
        if (operatorId != null) {
            sql.append(" and `operator_id`=?");
            args.add(operatorId);
        }
        if (action != null && !action.isBlank()) {
            sql.append(" and `action` like ?");
            args.add("%" + action.trim() + "%");
        }
        if (result != null) {
            sql.append(" and `result`=?");
            args.add(result);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" and `created_time` >= ?");
            args.add(from + " 00:00:00");
        }
        if (to != null && !to.isBlank()) {
            sql.append(" and `created_time` <= ?");
            args.add(to + " 23:59:59");
        }
        return sql.toString();
    }
}
