package com.user.service;

import com.model.bean.PageBean;
import com.model.bean.User;
import com.user.bean.UserAddress;

public interface UserService {

    String findPasswordByUsername(String username);

    User findIdAndPasswordByUsername(String username);

    User findUserByUsername(String username);

    User findOtherUserByUsername(String username);

    void registerInsert(String username,String password);

    void delete(String username);

    void update(Long id, String phone, String email);

    void updateAvatar(String avatar);
    //更新密码
    void updatePwd(String newPwd);
    //添加收货人的信息
    void addReceiverDetail(UserAddress userAddress);

    // ---------- 管理员：用户管理 ----------
    PageBean<User> pageUsers(Integer page, Integer size, String keyword);

    void adminUpdate(Long operatorId, Long id, Integer status, Integer role, String email, String phone);

    void adminResetPwd(Long operatorId, Long id, String newPassword);

    boolean isAdminExist();

    void seedAdmin(String username, String password);
}
