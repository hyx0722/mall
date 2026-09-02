package com.payment.feign.fall;

import com.model.bean.Order;
import com.model.bean.Result;
import com.payment.feign.OrderFeignClient;
import org.springframework.stereotype.Component;

@Component
public class OrderFeignClientFallback implements OrderFeignClient {

    @Override
    public Result<Order> findDetailOrder(Long id) {
        return Result.error("订单服务暂不可用");
    }
}
