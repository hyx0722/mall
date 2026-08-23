-- 创建订单数据库
CREATE DATABASE IF NOT EXISTS mall_service_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mall_service_order;

-- 1. 订单主表
CREATE TABLE `orders` (
                          `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
                          `order_no`          VARCHAR(32)     NOT NULL                COMMENT '订单编号（业务唯一）',
                          `user_id`           BIGINT UNSIGNED NOT NULL                COMMENT '买家用户ID（逻辑外键 -> user_db.user.id）',
                          `address_id`        BIGINT UNSIGNED DEFAULT NULL            COMMENT '收货地址ID（逻辑外键 -> user_db.user_address.id）',
                          `total_amount`      DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '订单总金额',
                          `discount_amount`   DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '优惠金额',
                          `order_status`      TINYINT         NOT NULL DEFAULT 0      COMMENT '订单状态：0-待付款，1-待发货，2-待收货，3-已完成，4-已取消，5-退款中，6-已退款',
                          `shipping_status`   TINYINT         NOT NULL DEFAULT 0      COMMENT '发货状态：0-未发货，1-已发货，2-已收货',
                          `shipping_time`     DATETIME        DEFAULT NULL            COMMENT '发货时间',
                          `complete_time`     DATETIME        DEFAULT NULL            COMMENT '完成时间',
                          `cancel_time`       DATETIME        DEFAULT NULL            COMMENT '取消时间',
                          `receiver_name`     VARCHAR(50)     DEFAULT NULL            COMMENT '收货人姓名（快照）',
                          `receiver_phone`    VARCHAR(20)     DEFAULT NULL            COMMENT '收货人手机号（快照）',
                          `receiver_address`  VARCHAR(255)    DEFAULT NULL            COMMENT '收货地址（快照）',
                          `remark`            VARCHAR(500)    DEFAULT NULL            COMMENT '订单备注',
                          `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_order_no` (`order_no`),
                          KEY `idx_user_id` (`user_id`),
                          KEY `idx_order_status` (`order_status`),
                          KEY `idx_created_at` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- 2. 订单明细表
CREATE TABLE `order_item` (
                              `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '明细ID',
                              `order_id`          BIGINT UNSIGNED NOT NULL                COMMENT '订单ID',
                              `product_id`        BIGINT UNSIGNED NOT NULL                COMMENT '商品ID（逻辑外键 -> product_db.product.id）',
                              `product_name`      VARCHAR(200)    NOT NULL                COMMENT '商品名称（快照）',
                              `product_image`     VARCHAR(255)    DEFAULT NULL            COMMENT '商品图片（快照）',
                              `product_price`     DECIMAL(10,2)   NOT NULL                COMMENT '商品单价（快照）',
                              `quantity`          INT UNSIGNED    NOT NULL DEFAULT 1      COMMENT '购买数量',
                              `total_price`       DECIMAL(10,2)   NOT NULL                COMMENT '小计金额（单价×数量）',
                              `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_order_id` (`order_id`),
                              KEY `idx_product_id` (`product_id`),
                              CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

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
