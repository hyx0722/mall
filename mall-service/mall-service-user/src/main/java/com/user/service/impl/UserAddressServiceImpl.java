package com.user.service.impl;

import com.mall.common.web.Auths;
import com.user.bean.UserAddress;
import com.user.mapper.UserAddressMapper;
import com.user.service.UserAddressService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class UserAddressServiceImpl implements UserAddressService {
    @Autowired
    UserAddressMapper userAddressMapper;

    public void addUserAddress(UserAddress userAddress){
        userAddressMapper.addUserAddress(userAddress);
    };

    public void updateUserAddressById(UserAddress userAddress, Long id){
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        userAddressMapper.updateUserAddressById(userAddress,id,userId);
    }

    public void deleteUserAddress(Long id){
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        // 仅按 id+归属条件删除，禁止越权删他人地址（勿用 BaseMapper.deleteById）
        userAddressMapper.deleteUserAddress(id,userId);
    }

    public UserAddress selectUserDetailAddress(Long id){
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        return userAddressMapper.selectUserDetailAddress(id,userId);
    };

    public List<UserAddress> selectUserAddress(Long userId){
        return userAddressMapper.selectUserAddress(userId);
    }
}
