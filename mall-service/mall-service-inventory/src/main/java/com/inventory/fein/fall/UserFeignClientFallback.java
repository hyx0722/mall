package com.inventory.fein.fall;



import com.inventory.fein.UserFeignClient;
import org.springframework.stereotype.Component;

@Component
public class UserFeignClientFallback implements UserFeignClient {
}
