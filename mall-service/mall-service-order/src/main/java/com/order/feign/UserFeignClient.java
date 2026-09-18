package com.order.feign;

import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.model.bean.Result;
import com.order.feign.fall.UserFeignClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用 user 服务的优惠券接口。券的定义与归属都在 user 服务，order 只负责在**下单事务内**
 * 同步调用它们——这是本仓第一个 order -> user 的 Feign 调用。
 *
 * 能调通的前提是 {@code FeignIdentityInterceptor} 会透传 Authorization：
 * user 服务的鉴权不走网关注入的 X-User-* 头，而是由自己的 LoginInterceptor 解析 JWT，
 * 缺了这个头会一律 401（见该拦截器的类注释）。
 *
 * 为什么核销是同步的、而退券走事件（{@code CouponReleaseListener}）：
 * 核销与「折扣写进订单」必须原子，异步会出现「订单已打折、券却没销掉」的白送窗口；
 * 而退券是补偿动作，事件驱动 + 有界重试反而更可靠，且不阻塞订单状态推进。
 */
@FeignClient(value = "mall-service-user", fallback = UserFeignClientFallback.class)
public interface UserFeignClient {

    /** 试算：只读，返回可用性与抵扣金额 */
    @PostMapping("/coupon/preview")
    Result<CouponPreviewResult> preview(@RequestBody CouponPreviewRequest request);

    /** 核销：条件 UPDATE，失败（券被并发用掉）返回错误码，调用方须让下单回滚 */
    @PostMapping("/coupon/use")
    Result<Void> use(@RequestParam("userCouponId") Long userCouponId,
                     @RequestParam("orderId") Long orderId);
}
