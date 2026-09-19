package com.user.config;


import com.user.service.UserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 启动自迁移/种子：
 * 1) 为已有库的 user 表补充 role 列（普通用户=1，管理员=2，默认 1）；
 * 2) 为已有库补建站内通知相关的三张表（订阅 / 公告 / 通知）；
 * 3) 若库中不存在任何管理员，按 mall.admin.username/password 自动创建一个（登录后可自行修改）。
 * 迁移失败仅告警不阻断启动，便于新库先手工建表。
 */
@Slf4j
@Component
public class UserStartupSetup implements ApplicationRunner {

    /** 默认弱口令：仅用于本地演示。作为 @Value 的兜底值，命中时启动日志会显式告警。 */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserAdminService userAdminService;

    @Value("${mall.admin.username:admin}")
    private String adminUsername;
    // 复用常量而非再写一遍字面量，避免兜底值与告警判断悄悄漂移
    @Value("${mall.admin.password:" + DEFAULT_ADMIN_PASSWORD + "}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        ensureRoleColumn();
        ensureNotificationTables();
        ensureAdmin();
    }

    /**
     * 补建站内通知相关的三张表（商店订阅 / 商店公告 / 站内通知）。
     *
     * ⚠️ 这里的 DDL 是 schema 的**第二份真相**（第一份是 resources/User.sql）。
     * 两处必须逐字一致：不一致时不会有任何编译期或运行期报错，只会在启动日志里留一条 WARN，
     * 然后所有涉及该列的查询开始 500。改表结构时**两边都要改**。
     *
     * 三条各自 try/catch：一张表建失败不该连累另外两张（也就不会连累后面的管理员种子）。
     */
    private void ensureNotificationTables() {
        for (String ddl : NOTIFICATION_DDL) {
            try {
                execute(ddl);
            } catch (Exception e) {
                log.warn("[user] 补建通知相关表失败（忽略，请手工执行 User.sql 中的对应 CREATE TABLE）: {}", e.getMessage());
            }
        }
    }

    /** 与 resources/User.sql 的第 6/7/8 张表保持一致 */
    private static final String[] NOTIFICATION_DDL = {
            "CREATE TABLE IF NOT EXISTS `store_subscription` ("
                    + "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订阅ID',"
                    + "`user_id` BIGINT UNSIGNED NOT NULL COMMENT '订阅者用户ID（逻辑外键 -> user.id）',"
                    + "`store_id` BIGINT UNSIGNED NOT NULL COMMENT '被订阅的商店ID（逻辑外键 -> user.id）',"
                    + "`created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订阅时间',"
                    + "PRIMARY KEY (`id`),"
                    + "UNIQUE KEY `uk_user_store` (`user_id`,`store_id`),"
                    + "KEY `idx_store` (`store_id`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店订阅表'",

            "CREATE TABLE IF NOT EXISTS `store_message` ("
                    + "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '公告ID',"
                    + "`store_id` BIGINT UNSIGNED NOT NULL COMMENT '发布公告的商店ID（逻辑外键 -> user.id）',"
                    + "`content` VARCHAR(500) NOT NULL COMMENT '公告内容',"
                    + "`created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',"
                    + "PRIMARY KEY (`id`),"
                    + "KEY `idx_store_time` (`store_id`,`id`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商店公告表'",

            "CREATE TABLE IF NOT EXISTS `notification` ("
                    + "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '通知ID',"
                    + "`user_id` BIGINT UNSIGNED NOT NULL COMMENT '收件人用户ID（逻辑外键 -> user.id）',"
                    + "`type` TINYINT NOT NULL COMMENT '类型：1下单 2支付 3发货 4完成 5取消 6退款 7商店公告 8上新 9新券',"
                    + "`title` VARCHAR(100) NOT NULL COMMENT '标题',"
                    + "`content` VARCHAR(500) NOT NULL COMMENT '正文',"
                    + "`ref_type` VARCHAR(16) NOT NULL DEFAULT '' COMMENT '关联业务类型：ORDER/PRODUCT/COUPON/STORE；空串=无关联',"
                    + "`ref_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联业务主键（依 ref_type 解释）；0=无关联',"
                    + "`store_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '来源商店ID；0=非商店来源',"
                    + "`is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：1-是，0-否',"
                    + "`created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
                    + "PRIMARY KEY (`id`),"
                    // ref_id / ref_type 必须 NOT NULL：唯一索引视多个 NULL 为互不相同，去重会静默失效
                    + "UNIQUE KEY `uk_user_type_ref` (`user_id`,`type`,`ref_id`),"
                    + "KEY `idx_user_time` (`user_id`,`id`),"
                    + "KEY `idx_user_unread` (`user_id`,`is_read`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户站内通知表'",
    };

    private void ensureRoleColumn() {
        try {
            int cnt = queryInt(
                    "select count(*) from information_schema.columns where table_schema=database() and table_name='user' and column_name='role'");
            if (cnt == 0) {
                execute("ALTER TABLE `user` ADD COLUMN `role` TINYINT NOT NULL DEFAULT 1 COMMENT '角色：1-普通用户 2-管理员' AFTER `status`");
                log.info("[user] user.role 列不存在，已自动补充");
            }
        } catch (Exception e) {
            log.warn("[user] 检查/补充 user.role 列失败（忽略，若未建表请先执行 User.sql）: {}", e.getMessage());
        }
    }

    private void ensureAdmin() {
        try {
            if (userAdminService.isAdminExist()) {
                return;
            }
            if (adminUsername == null || adminUsername.isBlank()) {
                return;
            }
            String pwd = (adminPassword == null || adminPassword.isBlank()) ? DEFAULT_ADMIN_PASSWORD : adminPassword;
            userAdminService.seedAdmin(adminUsername, pwd);
            // 不打印密码（启动日志常被采集/留存）：仅在仍为默认弱口令时告警，否则只记账号
            if (DEFAULT_ADMIN_PASSWORD.equals(pwd)) {
                log.warn("[user] 已初始化管理员账号 username={}，当前为默认弱口令，请立即登录后台修改密码", adminUsername);
            } else {
                log.info("[user] 已初始化管理员账号 username={}", adminUsername);
            }
        } catch (Exception e) {
            log.warn("[user] 初始化管理员失败（忽略）: {}", e.getMessage());
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private int queryInt(String sql) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
