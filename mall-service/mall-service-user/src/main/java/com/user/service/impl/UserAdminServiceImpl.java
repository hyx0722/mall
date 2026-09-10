package com.user.service.impl;

import com.model.bean.PageBean;
import com.model.bean.User;
import com.model.exception.BusinessException;
import com.user.mapper.UserAdminMapper;
import com.user.service.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class UserAdminServiceImpl implements UserAdminService {

    @Autowired
    private UserAdminMapper userAdminMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;




    @Override
    public PageBean<User> pageUsers(Integer page, Integer size, String keyword) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, 100);
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        long total = userAdminMapper.countUsers(kw);
        if (total == 0L) {
            return new PageBean<>(0L, List.of());
        }
        List<User> items = userAdminMapper.pageUsers(kw, (p - 1) * s, s);
        return new PageBean<>(total, items);
    }

    @Override
    public void adminUpdate(Long operatorId, Long id, Integer status, Integer role, String email, String phone) {
        if (id == null) {
            throw new BusinessException("缺少用户 id");
        }
        if (operatorId == null) {
            throw new BusinessException("请先登录");
        }
        User target = userAdminMapper.findUserById(id);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        // 不能改自己的角色或禁用自己，防止自锁 / 清空管理员
        boolean self = operatorId.equals(id);
        if (self && ((status != null && status == 0) || role != null)) {
            throw new BusinessException("不能修改自己的角色或禁用自己");
        }
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException("状态只能为 0(禁用)或 1(正常)");
        }
        if (role != null && role != 1 && role != 2) {
            throw new BusinessException("角色只能为 1(普通用户)或 2(管理员)");
        }
        try {
            int affected = userAdminMapper.adminUpdateFields(id, status, role, email, phone);
            if (affected == 0) {
                throw new BusinessException("更新失败，用户不存在");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException("邮箱或手机号已被其他用户占用");
        }
    }

    @Override
    public void adminResetPwd(Long operatorId, Long id, String newPassword) {
        if (operatorId == null) {
            throw new BusinessException("请先登录");
        }
        if (id == null) {
            throw new BusinessException("缺少用户 id");
        }
        if (newPassword == null || !newPassword.matches("^\\S{5,16}$")) {
            throw new BusinessException("新密码长度必须在5-16位且不能包含空格");
        }
        int affected = userAdminMapper.updatePasswordById(id, passwordEncoder.encode(newPassword));
        if (affected == 0) {
            throw new BusinessException("用户不存在");
        }
    }

    @Override
    public boolean isAdminExist() {
        return userAdminMapper.countAdmins() > 0;
    }

    @Override
    public void seedAdmin(String username, String password) {
        userAdminMapper.insertAdmin(username, passwordEncoder.encode(password));
    }
}
