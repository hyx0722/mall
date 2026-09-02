package com.user.config;

import com.user.service.UserService;
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

    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserService userService;

    @Value("${mall.admin.username:admin}")
    private String adminUsername;
    @Value("${mall.admin.password:admin123}")
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
            if (userService.isAdminExist()) {
                return;
            }
            if (adminUsername == null || adminUsername.isBlank()) {
                return;
            }
            String pwd = (adminPassword == null || adminPassword.isBlank()) ? "admin123" : adminPassword;
            userService.seedAdmin(adminUsername, pwd);
            log.info("[user] 已初始化管理员账号 username={} password={}（建议登录后台后修改密码）", adminUsername, pwd);
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
