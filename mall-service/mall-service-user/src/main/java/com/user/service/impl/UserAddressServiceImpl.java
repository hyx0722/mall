package com.user.service.impl;

import com.model.util.ThreadLocalUtil;
import com.user.bean.UserAddress;
import com.user.mapper.UserAddressMapper;
import com.user.service.UserAddressService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;

@Service
public class UserAddressServiceImpl implements UserAddressService {
    @Autowired
    UserAddressMapper userAddressMapper;

    public void addUserAddress(UserAddress userAddress){
        userAddressMapper.addUserAddress(userAddress);
    };

    public void updateUserAddressById(UserAddress userAddress, Long id){
        Map<String,Object> map = ThreadLocalUtil.get();
        Long userId = (Long) map.get("id");
        userAddressMapper.updateUserAddressById(userAddress,id,userId);
    }

    public void deleteUserAddress(Long id){
        Map<String,Object> map = ThreadLocalUtil.get();
        Long userId = (Long) map.get("id");
        // 仅按 id+归属条件删除，禁止越权删他人地址（勿用 BaseMapper.deleteById）
        userAddressMapper.deleteUserAddress(id,userId);
    }

    public UserAddress selectUserDetailAddress(Long id){
        Map<String,Object> map = ThreadLocalUtil.get();
        Long userId = (Long) map.get("id");
        return userAddressMapper.selectUserDetailAddress(id,userId);
    };

    public List<UserAddress> selectUserAddress(Long userId){
        return userAddressMapper.selectUserAddress(userId);
    }
}
