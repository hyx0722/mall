package com.order.fein.fall;



import com.order.fein.UserFeignClient;
import org.springframework.stereotype.Component;

@Component
public class UserFeignClientFallback implements UserFeignClient {
}
