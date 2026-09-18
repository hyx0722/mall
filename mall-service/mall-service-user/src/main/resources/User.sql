
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

-- 3. 优惠券模板（券的定义：管理员发平台券，商家发自家店铺券）
--    存量环境升级：
--      a) 若库里还没有本表，执行下面三条 CREATE TABLE 即可（无 ALTER）；
--      b) 若已按上一版建过本表，只需补卖家归属这一列与索引：
--         ALTER TABLE `coupon` ADD COLUMN `seller_id` BIGINT UNSIGNED NOT NULL DEFAULT 0
--             COMMENT '发券商家ID；0-平台券' AFTER `id`,
--             ADD KEY `idx_seller_status` (`seller_id`,`status`);
CREATE TABLE `coupon` (
                          `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
                          `seller_id`           BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '发券商家ID；0-平台券（管理员发，券中心可领），否则为商家券（仅在其店铺页可领）',
                          `name`                VARCHAR(100)    NOT NULL                COMMENT '券名称',
                          `coupon_type`         TINYINT         NOT NULL                COMMENT '类型：1-满减，2-折扣',
                          `threshold_amount`    DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '满减门槛：限定范围内小计须 >= 该值（满减券用；折扣券为 0）',
                          `discount_amount`     DECIMAL(10,2)   NOT NULL DEFAULT 0.00   COMMENT '满减面额（满减券用；折扣券为 0）',
                          `discount_rate`       DECIMAL(4,3)    NOT NULL DEFAULT 1.000  COMMENT '折扣率，如 0.850 表示 8.5 折（折扣券用；满减券为 1.000）',
                          `max_discount_amount` DECIMAL(10,2)   DEFAULT NULL            COMMENT '折扣封顶金额：折扣券抵扣不超过该值；NULL 表示不封顶',
                          `total_count`         INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '发放总量（领券的条件 UPDATE 依据：received_count < total_count）',
                          `received_count`      INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '已领取数量（并发抢券靠它做条件更新，不可用「先查后写」）',
                          `start_time`          DATETIME        NOT NULL                COMMENT '生效开始时间',
                          `end_time`            DATETIME        NOT NULL                COMMENT '生效结束时间',
                          `status`              TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1-启用，0-停用',
                          `created_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          KEY `idx_status_time` (`status`,`start_time`,`end_time`),
                          -- 店铺页要按商家筛可领券，商家中心也要按商家列自己的券
                          KEY `idx_seller_status` (`seller_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- 4. 券的适用范围（一个券可挂多条：指定商品 或 指定分类）
--    没有本表记录的券视为「全场通用」。
CREATE TABLE `coupon_scope` (
                                `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '适用范围ID',
                                `coupon_id`    BIGINT UNSIGNED NOT NULL                COMMENT '优惠券ID（逻辑外键 -> coupon.id）',
                                `scope_type`   TINYINT         NOT NULL                COMMENT '范围类型：1-商品，2-分类',
                                `scope_id`     BIGINT UNSIGNED NOT NULL                COMMENT '商品ID 或 分类ID（依 scope_type 解释）',
                                `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_coupon_id` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券适用范围表';

-- 5. 用户持有的券（归属在 user 服务，下单时由 order 服务同步调用本服务核销）
CREATE TABLE `user_coupon` (
                               `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '持有记录ID',
                               `user_id`       BIGINT UNSIGNED NOT NULL                COMMENT '用户ID（逻辑外键 -> user.id）',
                               `coupon_id`     BIGINT UNSIGNED NOT NULL                COMMENT '优惠券ID（逻辑外键 -> coupon.id）',
                               `status`        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态：0-未使用，1-已使用，2-已过期',
                               `order_id`      BIGINT UNSIGNED DEFAULT NULL            COMMENT '核销的订单ID（未使用时为 NULL）',
                               `received_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
                               `used_time`     DATETIME        DEFAULT NULL            COMMENT '使用时间',
                               PRIMARY KEY (`id`),
                               -- 每人每券限领 1 张：靠唯一键保证，而非「先查后插」——
                               -- 并发重复领取会撞键，业务层转成友好提示即可
                               UNIQUE KEY `uk_user_coupon` (`user_id`,`coupon_id`),
                               KEY `idx_user_status` (`user_id`,`status`),
                               -- 退券按 (id, status, order_id) 条件更新，用得上这个索引
                               KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

