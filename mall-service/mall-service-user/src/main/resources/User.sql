
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

-- 6. 商店订阅（买家 -> 商店）
--    「商店」在本仓没有独立实体：**商店就是卖家用户**（商品靠 product.user_id 归属，
--    店铺页路由是 /store/:username）。所以 store_id 指的是 user.id。
--    存量环境升级：库里没有本表时执行下面这条 CREATE TABLE 即可（无 ALTER）。
CREATE TABLE `store_subscription` (
                                      `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订阅ID',
                                      `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '订阅者用户ID（逻辑外键 -> user.id）',
                                      `store_id`     BIGINT UNSIGNED NOT NULL                COMMENT '被订阅的商店ID（逻辑外键 -> user.id）',
                                      `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',
                                      PRIMARY KEY (`id`),
                                      -- 重复订阅靠唯一键挡，不做「先查后插」（那是竞态来源）
                                      UNIQUE KEY `uk_user_store` (`user_id`,`store_id`),
                                      -- 发公告/上新/发券时按 store_id 群发订阅者
                                      KEY `idx_store` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店订阅表';

-- 7. 商店公告（商家自己发的消息，发布即群发给订阅者；店铺页对所有人可见）
CREATE TABLE `store_message` (
                                 `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公告ID',
                                 `store_id`     BIGINT UNSIGNED NOT NULL                COMMENT '发布公告的商店ID（逻辑外键 -> user.id）',
                                 `content`      VARCHAR(500)    NOT NULL                COMMENT '公告内容',
                                 `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
                                 PRIMARY KEY (`id`),
                                 -- 店铺页按店倒序取最新，商家中心按店列自己的
                                 KEY `idx_store_time` (`store_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店公告表';

-- 8. 用户站内通知（「我的消息」的数据源）
--    type：1下单 2支付 3发货 4完成 5取消 6退款 | 7商店公告 8上新 9新券
--    ref_type/ref_id 供前端深链到订单详情 / 商品详情 / 店铺页；store_id 供列表批量补全店铺用户名。
CREATE TABLE `notification` (
                                `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',
                                `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '收件人用户ID（逻辑外键 -> user.id）',
                                `type`         TINYINT         NOT NULL                COMMENT '类型：1下单 2支付 3发货 4完成 5取消 6退款 7商店公告 8上新 9新券',
                                `title`        VARCHAR(100)    NOT NULL                COMMENT '标题',
                                `content`      VARCHAR(500)    NOT NULL                COMMENT '正文',
                                `ref_type`     VARCHAR(16)     NOT NULL DEFAULT ''     COMMENT '关联业务类型：ORDER/PRODUCT/COUPON/STORE；空串=无关联',
                                `ref_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '关联业务主键（依 ref_type 解释）；0=无关联',
                                `store_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '来源商店ID；0=非商店来源。列表按它批量补全店铺用户名',
                                `is_read`      TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否已读：1-是，0-否',
                                `created_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                -- 幂等键：MQ 是 at-least-once，重投不能重复写通知；商店群发时同一
                                -- (type, ref_id) 要写给 N 个用户，故必须带 user_id。
                                --
                                -- ⚠️ ref_id / ref_type 刻意是 NOT NULL DEFAULT 0/''：
                                --    MySQL 唯一索引视多个 NULL 为互不相同，留 NULL 会让去重**静默**失效。
                                -- ⚠️ 必须含 type：一张订单合法地产生最多 6 条通知（类型 1-6），
                                --    只按 (user_id, ref_id) 去重会把它们全吞掉。
                                UNIQUE KEY `uk_user_type_ref` (`user_id`,`type`,`ref_id`),
                                -- 「全部」列表按收件人倒序翻页
                                KEY `idx_user_time` (`user_id`,`id`),
                                -- 未读数与「未读」筛选
                                KEY `idx_user_unread` (`user_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内通知表';

