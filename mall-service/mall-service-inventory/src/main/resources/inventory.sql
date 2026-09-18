CREATE DATABASE IF NOT EXISTS mall_service_inventory DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mall_service_inventory;

-- 1. 库存主表
CREATE TABLE `inventory` (
                             `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存记录ID',
                             `product_id`        BIGINT UNSIGNED NOT NULL                COMMENT '商品ID（逻辑外键 -> product_db.product.id）',
                             `user_id`           BIGINT UNSIGNED Not Null                COMMENT '商家ID（逻辑外键 -> user_db.user.id）',
                             `total_stock`       INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '总库存（实际物理库存）',
                             `locked_stock`      INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '锁定库存（下单未支付等占用）',
                             `available_stock`   INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '可用库存（= total_stock - locked_stock）',
                             `sales_count`       INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '累计销量（支付成功后增加）',
    -- ⚠️ 下面两列必须带 DEFAULT：InventoryMapperConcurrencyTest 用 updateInventory 的
    --    显式列名 INSERT 造数据，不含这些列——NOT NULL 且无默认值会让那个测试直接失败。
    --    存量环境升级：
    --      ALTER TABLE `inventory`
    --        ADD COLUMN `warn_threshold` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '库存预警阈值；0-不预警' AFTER `sales_count`,
    --        ADD COLUMN `last_warn_time` DATETIME DEFAULT NULL COMMENT '上次告警时间（冷却窗口去重用）' AFTER `warn_threshold`;
    `warn_threshold`    INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '库存预警阈值：available_stock <= 本值即告警；0-不预警',
    `last_warn_time`    DATETIME        DEFAULT NULL            COMMENT '上次告警时间（冷却窗口去重，避免每轮重复告警）',
                             `version`           INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
                             `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_product_id` (`product_id`),
                             KEY `idx_available_stock` (`available_stock`),
                             -- 预警扫描按「阈值 > 0」筛，绝大多数行 warn_threshold=0，走这个索引能少扫一遍全表
                             KEY `idx_warn_threshold` (`warn_threshold`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品库存表';

-- 2. 库存流水表
CREATE TABLE `inventory_log` (
                                 `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水ID',
                                 `product_id`        BIGINT UNSIGNED NOT NULL                COMMENT '商品ID（逻辑外键）',
                                 `order_id`          BIGINT UNSIGNED DEFAULT NULL            COMMENT '关联订单ID（逻辑外键 -> order_db.orders.id），非订单操作可为空',
                                 `change_type`       TINYINT         NOT NULL                COMMENT '变动类型：1-入库，2-出库，3-锁定，4-释放锁定，5-扣减库存，6-退货入库等',
                                 `change_quantity`   INT             NOT NULL                COMMENT '变动数量（正数表示增加，负数表示减少）',
                                 `before_total_stock` INT UNSIGNED   NOT NULL                COMMENT '变动前总库存',
                                 `after_total_stock`  INT UNSIGNED   NOT NULL                COMMENT '变动后总库存',
                                 `before_locked_stock` INT UNSIGNED  NOT NULL                COMMENT '变动前锁定库存',
                                 `after_locked_stock`  INT UNSIGNED  NOT NULL                COMMENT '变动后锁定库存',
                                 `remark`            VARCHAR(255)    DEFAULT NULL            COMMENT '备注',
                                 `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_product_id` (`product_id`),
                                 KEY `idx_order_id` (`order_id`),
                                 KEY `idx_created_at` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水表';

-- 幂等唯一键：同一订单同一商品同类型流水至多一条，作为消费幂等的 DB 兜底
-- （order_id 为 NULL 的入库等行不受唯一约束影响；存量库若已有重复需先清理再执行）
ALTER TABLE `inventory_log`
    ADD UNIQUE KEY `uk_order_product_type` (`order_id`,`product_id`,`change_type`);

-- 3. 事务性发件箱（outbox）：扣减结果回执（inventory.deducted / deduct_failed）在此入箱，
--    由 relay 定时投递到 mall.order.exchange。结构与 order / payment 两库同名表一致。
--
--    ⚠️ 存量环境升级：**必须手工执行下面这条 CREATE TABLE**，否则 inventory 服务启动后
--    relay 每 3 秒会因「表不存在」报错（业务消费本身不受影响，但回执发不出去）。
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

