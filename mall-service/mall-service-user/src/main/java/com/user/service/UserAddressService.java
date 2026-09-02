package com.user.service;

import com.user.bean.UserAddress;

import java.util.List;


public interface UserAddressService {
    void addUserAddress(UserAddress userAddress);

    void updateUserAddressById(UserAddress userAddress, Long id);

    void deleteUserAddress(Long id);

    UserAddress selectUserDetailAddress(Long id);

    List<UserAddress> selectUserAddress(Long userId);
}
