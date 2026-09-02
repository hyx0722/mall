-- 创建商品数据库
CREATE DATABASE IF NOT EXISTS mall_service_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mall_service_product;

-- 1. 商品分类表
CREATE TABLE `category` (
                            `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类ID',
                            `parent_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父分类ID，0表示顶级分类',
                            `name`       VARCHAR(50)     NOT NULL                COMMENT '分类名称',
                            `sort_order` INT             NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
                            `status`     TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1-启用，0-禁用',
                            `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `updated_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 2. 商品表
CREATE TABLE `product` (
                           `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
                           `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '所属用户ID（逻辑外键 -> user_db.user.id）',
                           `category_id`    BIGINT UNSIGNED NOT NULL                COMMENT '分类ID',
                           `name`           VARCHAR(200)    NOT NULL                COMMENT '商品名称',
                           `subtitle`       VARCHAR(200)    DEFAULT NULL            COMMENT '副标题/卖点',
                           `main_image`     VARCHAR(255)    DEFAULT NULL            COMMENT '主图URL',
                           `detail`         TEXT            DEFAULT NULL            COMMENT '商品详情（富文本）',
                           `price`          DECIMAL(10,2)   NOT NULL                COMMENT '售价',
                           `original_price` DECIMAL(10,2)   DEFAULT NULL            COMMENT '原价（用于展示划线价）',
                           `status`         TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1-上架，0-下架',
                           `created_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `updated_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`),                -- 逻辑外键索引
                           KEY `idx_category_id` (`category_id`),
                           KEY `idx_name` (`name`),
                           UNIQUE KEY `uk_user_name` (`user_id`,`name`),  -- 防止同一商家重复上架同名商品
                           CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

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
