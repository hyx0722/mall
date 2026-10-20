package com.user.controller;

import com.mall.common.audit.AuditLog;
import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Result;
import com.model.bean.User;
import com.model.constant.RedisKeys;
import com.user.bean.UserAdminResetPwdRequest;
import com.user.bean.UserAdminUpdateRequest;
import com.user.service.UserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员内部系统：用户管理。
 * 全部接口要求管理员（Auths.requireAdmin，角色来自本地 LoginInterceptor 解析 JWT claims）。
 * 经网关访问：/user/admin/listUsers 等。
 *
 * <p>类上的 {@link AuditLog} 让本控制器的写操作自动进入审计日志（GET 会被切面跳过）。</p>
 */
@Slf4j
@RestController
@Validated
@AuditLog(target = "USER")
public class UserAdminController {

    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 分页查询所有用户
    @GetMapping("/admin/listUsers")
    public Result<PageBean<User>> listUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Auths.requireAdmin();
        return Result.success(userAdminService.pageUsers(page, size, keyword));
    }

    // 管理后台看板的用户侧统计（概览 + 近 N 天注册趋势）
    @GetMapping("/admin/stats")
    public Result<Map<String, Object>> stats(@RequestParam(defaultValue = "7") Integer days) {
        Auths.requireAdmin();
        return Result.success(userAdminService.dashboard(days));
    }

    // 修改任意用户：status(启/禁用)、role(普通/管理员)、email、phone
    @AuditLog("修改用户")
    @PutMapping("/admin/updateUser")
    public Result updateUser(@RequestBody @Validated UserAdminUpdateRequest req) {
        Auths.requireAdmin();
        Long operatorId = Auths.currentUserId();
        String email = blankToNull(req.getEmail());
        String phone = blankToNull(req.getPhone());
        userAdminService.adminUpdate(operatorId, req.getId(), req.getStatus(), req.getRole(), email, phone);
        log.info("管理员{}修改了id为{}的用户", operatorId, req.getId());
        // 禁用或取消管理员后，目标旧 token 立即失效（避免降权后仍带旧角色头访问）
        boolean revoke = (req.getStatus() != null && req.getStatus() == 0)
                || (req.getRole() != null && req.getRole() == 1);
        if (revoke) {
            stringRedisTemplate.delete(RedisKeys.loginToken(req.getId()));
        }
        return Result.success();
    }

    // 重置任意用户密码（重置后强制其重新登录）
    @AuditLog("重置用户密码")
    @PatchMapping("/admin/resetPwd")
    public Result resetPwd(@RequestBody @Validated UserAdminResetPwdRequest req) {
        Auths.requireAdmin();
        userAdminService.adminResetPwd(Auths.currentUserId(), req.getId(), req.getNewPassword());
        stringRedisTemplate.delete(RedisKeys.loginToken(req.getId()));
        log.info("管理员{}重置了id为{}的用户的密码", Auths.currentUserId(), req.getId());
        return Result.success();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
