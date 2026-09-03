-- 创建支付数据库
CREATE DATABASE IF NOT EXISTS mall_service_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mall_service_payment;

-- 1. 支付订单表
CREATE TABLE `pay_order` (
                             `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '支付单ID',
                             `pay_no`            VARCHAR(32)     NOT NULL                COMMENT '支付单号（业务唯一）',
                             `order_id`          BIGINT UNSIGNED NOT NULL                COMMENT '关联订单ID（逻辑外键 -> order_db.orders.id）',
                             `user_id`           BIGINT UNSIGNED NOT NULL                COMMENT '买家用户ID（逻辑外键 -> user_db.user.id）',
                             `pay_amount`        DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '支付金额（实付金额）',
                             `payment_method`    TINYINT         NOT NULL DEFAULT 1      COMMENT '支付方式：1-支付宝，2-微信，3-银行卡，4-货到付款',
                             `payment_status`    TINYINT         NOT NULL DEFAULT 0      COMMENT '支付状态：0-待支付，1-支付成功，2-已退款，3-支付失败',
                             `transaction_id`    VARCHAR(64)     DEFAULT NULL            COMMENT '第三方支付流水号（支付宝交易号/微信支付单号）',
                             `pay_time`          DATETIME        DEFAULT NULL            COMMENT '支付时间',
                             `expire_time`       DATETIME        DEFAULT NULL            COMMENT '支付过期时间',
                             `created_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_pay_no` (`pay_no`),
                             KEY `idx_order_id` (`order_id`),
                             KEY `idx_user_id` (`user_id`),
                             KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';

-- 2. 支付回调记录表（第三方支付异步通知落库）
CREATE TABLE `payment_record` (
                                 `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                 `pay_order_id`      BIGINT UNSIGNED NOT NULL                COMMENT '支付单ID（逻辑外键 -> pay_order.id）',
                                 `pay_no`            VARCHAR(32)     NOT NULL                COMMENT '支付单号',
                                 `transaction_id`    VARCHAR(64)     DEFAULT NULL            COMMENT '第三方支付流水号',
                                 `notify_type`       VARCHAR(32)     DEFAULT NULL            COMMENT '回调类型：支付通知/退款通知',
                                 `notify_content`    TEXT            DEFAULT NULL            COMMENT '第三方回调原始报文',
                                 `handle_status`     TINYINT         NOT NULL DEFAULT 0      COMMENT '处理状态：0-未处理，1-处理成功，2-处理失败',
                                 `created_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_pay_order_id` (`pay_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付回调记录表';

-- 3. 退款表
CREATE TABLE `refund` (
                          `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '退款ID',
                          `refund_no`         VARCHAR(32)     NOT NULL                COMMENT '退款单号（业务唯一）',
                          `order_id`          BIGINT UNSIGNED NOT NULL                COMMENT '关联订单ID（逻辑外键 -> order_db.orders.id）',
                          `pay_order_id`      BIGINT UNSIGNED NOT NULL                COMMENT '支付单ID（逻辑外键 -> pay_order.id）',
                          `user_id`           BIGINT UNSIGNED NOT NULL                COMMENT '买家用户ID（逻辑外键 -> user_db.user.id）',
                          `refund_amount`     DECIMAL(10,2)   NOT NULL                COMMENT '退款金额',
                          `refund_status`     TINYINT         NOT NULL DEFAULT 0      COMMENT '退款状态：0-退款中，1-退款成功，2-退款失败',
                          `refund_reason`     VARCHAR(255)    DEFAULT NULL            COMMENT '退款原因',
                          `refund_time`       DATETIME        DEFAULT NULL            COMMENT '退款完成时间',
                          `created_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_time`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_refund_no` (`refund_no`),
                          KEY `idx_order_id` (`order_id`),
                          KEY `idx_pay_order_id` (`pay_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款表';

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

-- 4. 事务性发件箱（outbox）：pay.success 与支付落库同事务写入，relay 定时投递到 order。
--    已按本模块建库的存量环境：单独执行下面 CREATE TABLE 即可。
CREATE TABLE `outbox` (
                           `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '发件箱ID',
                           `exchange`     VARCHAR(100)    NOT NULL COMMENT '目标交换机',
                           `routing_key`  VARCHAR(100)    NOT NULL COMMENT '目标路由键',
                           `payload`      TEXT            NOT NULL COMMENT '事件 JSON 原文',
                           `status`       TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待发送 1-已发送',
                           `retry_count`  INT             NOT NULL DEFAULT 0 COMMENT '投递失败重试次数',
                           `delay_ms`     BIGINT          DEFAULT NULL COMMENT '非空则走延迟交换机并附加 per-message TTL(毫秒)',
                           `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `sent_time`    DATETIME        DEFAULT NULL COMMENT '成功投递时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_status_id` (`status`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务性发件箱';
