package com.user.controller;


import com.model.bean.Product;
import com.model.bean.Result;
import com.model.util.ThreadLocalUtil;
import com.user.fein.InventoryFeignClient;
import com.user.fein.ProductFeignClient;
import com.user.service.UserService;

import lombok.extern.slf4j.Slf4j;

import org.apache.seata.spring.annotation.GlobalTransactional;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;


import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@Slf4j
@Validated
public class UserNumController {
    @Autowired
    UserService userService;
    @Autowired
    RedissonClient redissonClient;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ProductFeignClient productFeignClient;
    @Autowired
    InventoryFeignClient inventoryFeignClient;

    //商家添加商品
    @PostMapping("userToAddProduct")
    @GlobalTransactional
    public Result userToAddProduct(@RequestBody @Validated Product product){
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        product.setUserId(userId);
        //添加产品
        productFeignClient.addNumProduct(product);
        //添加初始库存
        inventoryFeignClient.addNumInventory(product);
        return Result.success();
    }





}
