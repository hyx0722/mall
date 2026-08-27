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

    public void updateUserAddressById(UserAddress userAddress,Integer id){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        userAddressMapper.updateUserAddressById(userAddress,id,userId);
    }

    public void deleteUserAddress(Integer id){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        userAddressMapper.deleteById(id);
        userAddressMapper.deleteUserAddress(id,userId);
    }

    public UserAddress selectUserDetailAddress(Integer id){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return userAddressMapper.selectUserDetailAddress(id,userId);
    };

    public List<UserAddress> selectUserAddress(Integer userId){
        return userAddressMapper.selectUserAddress(userId);
    }
}
