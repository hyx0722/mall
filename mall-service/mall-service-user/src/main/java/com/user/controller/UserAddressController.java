package com.user.controller;

import com.model.bean.Result;
import com.user.bean.UserAddress;
import com.user.service.UserAddressService;
import com.model.util.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Validated
public class UserAddressController {
    @Autowired
    UserAddressService userAddressService;

    //添加收货信息
    @PostMapping("/addUserAddress")
    public Result addUserAddress(@RequestBody @Validated UserAddress userAddress){
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userAddress.setUserId(id);
        userAddressService.addUserAddress(userAddress);
        return Result.success();
    };

    //修改用户具体收货地址
    @PostMapping("/updateUserAddressById")
    public Result updateUserAddressById(@RequestBody @Validated UserAddress userAddress,@RequestParam("id") Integer id){
        userAddressService.updateUserAddressById(userAddress,id);
        return Result.success();
    }

    @DeleteMapping("/deleteUserAddress")
    public Result deleteUserAddress( @RequestParam("id") Integer id){
        userAddressService.deleteUserAddress(id);
        return Result.success();
    }

    //查找用户具体某个收货地址
    @GetMapping("/selectUserDetailAddress")
    public Result<UserAddress> selectUserDetailAddress(@RequestParam("id") Integer id){
        return Result.success(userAddressService.selectUserDetailAddress(id));
    }

    //查找用户具体所有收货地址
    @GetMapping("/selectUserAddress")
    public Result<List<UserAddress>> selectUserAddress(){
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return Result.success(userAddressService.selectUserAddress(userId));
    }


}
