package com.order.feign.fall;

import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.model.bean.Result;
import com.order.feign.UserFeignClient;
import org.springframework.stereotype.Component;

/**
 * 用户服务不可用时的降级：**一律返回错误，让下单失败**。
 *
 * 刻意不「降级成不用券」——那会让买家在下单页看到的抵扣额与实际支付金额不一致，
 * 等于静默多收钱。宁可下单失败并提示稍后重试。
 * 未使用券的下单（userCouponId 为空）根本不会走到这里。
 */
@Component
public class UserFeignClientFallback implements UserFeignClient {

    @Override
    public Result<CouponPreviewResult> preview(CouponPreviewRequest request) {
        return Result.error("优惠券服务暂不可用，请稍后重试");
    }

    @Override
    public Result<Void> use(Long userCouponId, Long orderId) {
        return Result.error("优惠券服务暂不可用，请稍后重试");
    }
}
