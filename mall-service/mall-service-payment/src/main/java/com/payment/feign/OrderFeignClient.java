package com.payment.feign;

import com.model.bean.Order;
import com.model.bean.Result;
import com.payment.feign.fall.OrderFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 拉取订单信息（金额/状态/归属）。
 * 复用 order 侧登录态端点 GET /findDetailOrder?id=：
 * 身份经 FeignIdentityInterceptor 从当前入站请求透传 X-User-Id，
 * 归属校验（id AND user_id）在 order 侧服务端完成，避免泄露他人订单。
 */
@FeignClient(value = "mall-service-order", fallback = OrderFeignClientFallback.class)
public interface OrderFeignClient {

    @GetMapping("/findDetailOrder")
    Result<Order> findDetailOrder(@RequestParam("id") Long id);
}
