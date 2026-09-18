package com.user.controller;

import com.mall.common.web.Auths;
import com.model.bean.Result;
import com.user.bean.Coupon;
import com.user.bean.CouponCreateRequest;
import com.user.service.CouponService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家发券（店铺券）。对外路径是 {@code /user/seller/coupon/*}（网关 StripPrefix 掉 /user）。
 *
 * 与管理员建券的 {@link CouponAdminController} 分开，是因为**归属来源不同**：
 * 管理员是平台券（seller_id=0，券中心可领），商家券的 seller_id **只取登录态**——
 * 请求体里根本没有这个字段，客户端无从伪造成别家店铺的券。这与
 * {@code userToAddProduct} 从登录态取上架者 id 是同一套做法。
 *
 * 商家身份不做额外校验：任何登录用户都能开店发券（本仓卖家与买家不区分角色），
 * 但**只能给自己的商品发**，归属由 {@link CouponService#createForSeller} 逐个校验。
 */
@RestController
@Validated
public class SellerCouponController {

    @Autowired
    CouponService couponService;

    /** 我发的券（含停用与已领完） */
    @GetMapping("/seller/coupon/list")
    public Result sellerList() {
        Auths.requireLogin();
        List<Coupon> list = couponService.sellerCoupons(Auths.currentUserId());
        return Result.success(list);
    }

    /** 给自家商品发券：scopes 必须都是自己的商品 */
    @PostMapping("/seller/coupon/create")
    public Result create(@RequestBody @Valid CouponCreateRequest request) {
        Auths.requireLogin();
        couponService.createForSeller(Auths.currentUserId(), request);
        return Result.success();
    }

    /** 启停自家的券；status：1-启用，0-停用 */
    @PutMapping("/seller/coupon/status")
    public Result updateStatus(@RequestParam @NotNull Long couponId, @RequestParam @NotNull Integer status) {
        Auths.requireLogin();
        couponService.updateStatusForSeller(Auths.currentUserId(), couponId, status);
        return Result.success();
    }
}
