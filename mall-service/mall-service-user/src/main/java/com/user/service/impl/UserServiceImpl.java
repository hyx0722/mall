package com.user.service.impl;

import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.User;
import com.model.exception.BusinessException;
import com.user.bean.UserAddress;
import com.user.mapper.UserMapper;
import com.user.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl  implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public String findPasswordByUsername(String username){
        return userMapper.findPasswordByUserName(username);
    }

    @Override
    public User findIdAndPasswordByUsername(String username) {
        return userMapper.findIdAndPasswordByUserName(username);
    }

    @Override
    public User findUserByUsername(String username) {
        return userMapper.findUserByUsername(username);
    }

    @Override
    public User findOtherUserByUsername(String username) {
        return userMapper.findOtherUserByUsername(username);
    }

    public void registerInsert(String username,String password){
        String hashedPassword = passwordEncoder.encode(password);
        userMapper.registerInsert(username,hashedPassword);
    }

    public void delete(String username){
        userMapper.delete(username);
    }

    @Override
    public void update(Long id, String phone, String email) {
        userMapper.update(id, phone, email);
    }

    @Override
    public void updateAvatar(String avatar) {
        Auths.requireLogin();
        Long id = Auths.currentUserId();
        userMapper.updateAvatar(avatar,id);
    }

    @Override
    public void updatePwd(String newPwd) {
        Auths.requireLogin();
        Long id = Auths.currentUserId();
        userMapper.updatePwd(passwordEncoder.encode(newPwd),id);
    }

    @Override
    public void addReceiverDetail(UserAddress userAddress) {
        userMapper.addReceiverDetail(userAddress);
    }

}
