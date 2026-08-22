package com.product.fein.fall;

import com.product.fein.UserFeignClient;

import org.springframework.stereotype.Component;

@Component
public class UserFeignClientFallback implements UserFeignClient {
}
