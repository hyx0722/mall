package com.user.service;

import com.model.bean.PageBean;
import com.model.bean.User;

import java.util.Map;

public interface UserAdminService {
    // ---------- 管理员：用户管理 ----------
    PageBean<User> pageUsers(Integer page, Integer size, String keyword);

    /** 管理后台看板的用户侧统计：概览 + 近 N 天注册趋势 */
    Map<String, Object> dashboard(int days);

    void adminUpdate(Long operatorId, Long id, Integer status, Integer role, String email, String phone);

    void adminResetPwd(Long operatorId, Long id, String newPassword);

    boolean isAdminExist();

    void seedAdmin(String username, String password);
}
