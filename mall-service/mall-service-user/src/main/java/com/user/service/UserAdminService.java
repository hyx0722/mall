package com.user.service;

import com.model.bean.PageBean;
import com.model.bean.User;

public interface UserAdminService {
    // ---------- 管理员：用户管理 ----------
    PageBean<User> pageUsers(Integer page, Integer size, String keyword);

    void adminUpdate(Long operatorId, Long id, Integer status, Integer role, String email, String phone);

    void adminResetPwd(Long operatorId, Long id, String newPassword);

    boolean isAdminExist();

    void seedAdmin(String username, String password);
}
