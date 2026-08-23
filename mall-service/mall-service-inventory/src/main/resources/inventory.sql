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
                             `version`           INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
                             `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_product_id` (`product_id`),
                             UNIQUE KEY `uk_user_id` (`user_id`),
                             KEY `idx_available_stock` (`available_stock`)
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

CREATE TABLE `undo_log` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `branch_id` bigint NOT NULL COMMENT '分支事务ID',
                            `xid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8_general_ci NOT NULL COMMENT '全局事务唯一标识',
                            `context` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8_general_ci NOT NULL COMMENT '上下文',
                            `rollback_info` longblob NOT NULL COMMENT '回滚信息',
                            `log_status` int NOT NULL COMMENT '状态，0正常，1全局已完成（防悬挂）',
                            `log_created` datetime NOT NULL COMMENT '创建时间',
                            `log_modified` datetime NOT NULL COMMENT '修改时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='AT模式回滚日志表';
