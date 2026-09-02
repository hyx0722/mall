
-- 创建用户数据库
CREATE DATABASE IF NOT EXISTS mall_service_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mall_service_user;

-- 1. 用户表
CREATE TABLE `user` (
                        `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                        `username`      VARCHAR(50)     NOT NULL                COMMENT '用户名',
                        `password`      VARCHAR(100)    NOT NULL                COMMENT '密码（加密存储）',
                        `email`         VARCHAR(100)    DEFAULT NULL            COMMENT '邮箱',
                        `phone`         VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
                        `avatar`        VARCHAR(255)    DEFAULT NULL            COMMENT '头像URL',
                        `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1-正常，0-禁用',
                        `role`          TINYINT         NOT NULL DEFAULT 1      COMMENT '角色：1-普通用户，2-管理员',
                        `created_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `updated_time`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`),
                        UNIQUE KEY `uk_email` (`email`),
                        UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 用户收货地址表
CREATE TABLE `user_address` (
                                `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '地址ID',
                                `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '用户ID（逻辑外键 -> user.id）',
                                `receiver_name`  VARCHAR(50)     NOT NULL                COMMENT '收货人姓名',
                                `receiver_phone` VARCHAR(20)     NOT NULL                COMMENT '收货人手机号',
                                `province`       VARCHAR(50)     DEFAULT NULL            COMMENT '省份',
                                `city`           VARCHAR(50)     DEFAULT NULL            COMMENT '城市',
                                `district`       VARCHAR(50)     DEFAULT NULL            COMMENT '区/县',
                                `detail_address` VARCHAR(255)    NOT NULL                COMMENT '详细地址',
                                `is_default`     TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否默认地址：1-是，0-否',
                                `created_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_user_id` (`user_id`),
                                CONSTRAINT `fk_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址表';

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
