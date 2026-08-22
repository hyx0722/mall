package com.user.service;

import com.user.bean.UserAddress;

import java.util.List;


public interface UserAddressService {
    void addUserAddress(UserAddress userAddress);

    void updateUserAddressById(UserAddress userAddress,Integer id);

    void deleteUserAddress(Integer id);

    UserAddress selectUserDetailAddress(Integer id);

    List<UserAddress> selectUserAddress(Integer userId);
}
