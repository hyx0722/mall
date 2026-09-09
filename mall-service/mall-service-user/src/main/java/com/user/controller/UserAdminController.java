package com.user.controller;

import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Result;
import com.model.bean.User;
import com.user.bean.UserAdminResetPwdRequest;
import com.user.bean.UserAdminUpdateRequest;
import com.user.service.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员内部系统：用户管理。
 * 全部接口要求管理员（Auths.requireAdmin，角色来自本地 LoginInterceptor 解析 JWT claims）。
 * 经网关访问：/user/admin/listUsers 等。
 */
@RestController
@Validated
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

    // 修改任意用户：status(启/禁用)、role(普通/管理员)、email、phone
    @PutMapping("/admin/updateUser")
    public Result updateUser(@RequestBody @Validated UserAdminUpdateRequest req) {
        Auths.requireAdmin();
        Long operatorId = Auths.currentUserId();
        String email = blankToNull(req.getEmail());
        String phone = blankToNull(req.getPhone());
        userAdminService.adminUpdate(operatorId, req.getId(), req.getStatus(), req.getRole(), email, phone);
        // 禁用或取消管理员后，目标旧 token 立即失效（避免降权后仍带旧角色头访问）
        boolean revoke = (req.getStatus() != null && req.getStatus() == 0)
                || (req.getRole() != null && req.getRole() == 1);
        if (revoke) {
            stringRedisTemplate.delete("login:token:" + req.getId());
        }
        return Result.success();
    }

    // 重置任意用户密码（重置后强制其重新登录）
    @PatchMapping("/admin/resetPwd")
    public Result resetPwd(@RequestBody @Validated UserAdminResetPwdRequest req) {
        Auths.requireAdmin();
        userAdminService.adminResetPwd(Auths.currentUserId(), req.getId(), req.getNewPassword());
        stringRedisTemplate.delete("login:token:" + req.getId());
        return Result.success();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
