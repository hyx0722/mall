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
 * 2) 若库中不存在任何管理员，按 mall.admin.username/password 自动创建一个（登录后可自行修改）。
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
        ensureAdmin();
    }

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
