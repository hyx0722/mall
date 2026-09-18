package com.user.controller;

import com.mall.common.web.Auths;
import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.model.bean.Result;
import com.user.bean.Coupon;
import com.user.bean.UserCoupon;
import com.user.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券（user 服务）。
 *
 * 买家接口由本地 LoginInterceptor 兜住（它挂在 /**，只排除 /login、/register），
 * 故这里不再重复鉴权，只取身份。
 *
 * `/preview` `/use` `/release` 三条是**给 order 服务在下单链路里同步调用的**，
 * 不是给前端直接调的——它们同样要求登录态，但语义上属于服务间契约：
 * order 的 FeignIdentityInterceptor 会把 Authorization 与 X-User-* 一并透传过来。
 */
@RestController
@Validated
public class CouponController {

    @Autowired
    CouponService couponService;

    // ---------- 买家 ----------

    /** 券中心：可领取的**平台券**（商家券只在各自店铺页露出） */
    @GetMapping("/coupon/center")
    public Result center() {
        Auths.requireLogin();
        return Result.success(couponService.center());
    }

    /** 店铺页：某商家当前可领的券（按用户名定位商家） */
    @GetMapping("/coupon/store")
    public Result storeCoupons(@RequestParam String username) {
        Auths.requireLogin();
        return Result.success(couponService.storeCoupons(username));
    }

    @PostMapping("/coupon/receive")
    public Result receive(@RequestParam @NotNull Long couponId) {
        Auths.requireLogin();
        couponService.receive(Auths.currentUserId(), couponId);
        return Result.success();
    }

    /** 我的券；status 不传即全部（0未使用 1已使用 2已过期） */
    @GetMapping("/coupon/mine")
    public Result mine(@RequestParam(required = false) Integer status) {
        Auths.requireLogin();
        List<UserCoupon> list = couponService.mine(Auths.currentUserId(), status);
        return Result.success(list);
    }

    /**
     * 结算页可用券列表。body 与 /coupon/preview 同形（传商品快照明细），
     * 但会一次性评估用户所有未使用的券并带上抵扣额，供前端渲染券选择器。
     */
    @PostMapping("/coupon/usable")
    public Result usable(@RequestBody @Valid CouponPreviewRequest request) {
        Auths.requireLogin();
        return Result.success(couponService.usableCoupons(Auths.currentUserId(), request.getLines()));
    }

    // ---------- 供 order 服务调用 ----------

    /** 试算：只读。不可用时返回 usable=false + 原因，由 order 决定是否中止下单 */
    @PostMapping("/coupon/preview")
    public Result preview(@RequestBody @Valid CouponPreviewRequest request) {
        Auths.requireLogin();
        CouponPreviewResult result = couponService.preview(Auths.currentUserId(), request);
        return Result.success(result);
    }

    /** 核销：下单事务内调用；失败须抛出让下单整体回滚 */
    @PostMapping("/coupon/use")
    public Result use(@RequestParam @NotNull Long userCouponId, @RequestParam @NotNull Long orderId) {
        Auths.requireLogin();
        couponService.use(Auths.currentUserId(), userCouponId, orderId);
        return Result.success();
    }

    // 这里刻意**没有** /coupon/release：退券只在订单真的取消/退款后由 MQ 事件触发
    // （见 CouponReleaseListener）。一旦开成 HTTP 接口、参数由客户端给，
    // 买家就能用券下单拿到折扣后立刻把券要回来重复抵扣。
}
