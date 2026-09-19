-- outbox 表 DDL，供 OutboxServiceImplTest 起真实 MySQL 用。
--
-- ⚠️ 这份 DDL 镜像自 order.sql / payment.sql / inventory.sql 里那张同名表。
--    三个服务的 outbox 表结构是逐字相同的（OutboxServiceImpl 用同一组 SQL 操作它们）。
--    改那三处时请同步改这里，否则本测试验的就不是线上那张表了。
--
-- 放在 mall-common 的 test resources 而不是复用某个服务的 *.sql：mall-common 是共享模块，
-- 不该反向依赖任何单个服务的源码目录。
CREATE TABLE `outbox` (
                           `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '发件箱ID',
                           `exchange`     VARCHAR(100)    NOT NULL COMMENT '目标交换机',
                           `routing_key`  VARCHAR(100)    NOT NULL COMMENT '目标路由键',
                           `payload`      TEXT            NOT NULL COMMENT '事件 JSON 原文',
                           `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待发送 1-已发送 3-已放弃(无法路由超限，需人工介入)',
                           `retry_count`  INT             NOT NULL DEFAULT 0 COMMENT '投递失败重试次数',
                           `delay_ms`     BIGINT          DEFAULT NULL COMMENT '非空则走延迟交换机并附加 per-message TTL(毫秒)',
                           `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `sent_time`    DATETIME        DEFAULT NULL COMMENT '成功投递时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_status_id` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务性发件箱';
