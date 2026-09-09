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

}
