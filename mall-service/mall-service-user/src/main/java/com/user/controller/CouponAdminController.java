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

/** 优惠券管理端。与其它 /admin/* 一致：网关无 /admin 路由，对外是 /user/admin/coupon/*。 */
@RestController
@Validated
public class CouponAdminController {

    @Autowired
    CouponService couponService;

    /** 全量券（含停用与已领完），可按名称模糊 */
    @GetMapping("/admin/coupon/list")
    public Result list(@RequestParam(required = false) String keyword) {
        Auths.requireAdmin();
        List<Coupon> list = couponService.listAll(keyword);
        return Result.success(list);
    }

    @PostMapping("/admin/coupon/create")
    public Result create(@RequestBody @Valid CouponCreateRequest request) {
        Auths.requireAdmin();
        couponService.create(request);
        return Result.success();
    }

    /** 上下架；status：1-启用，0-停用 */
    @PutMapping("/admin/coupon/status")
    public Result updateStatus(@RequestParam @NotNull Long couponId, @RequestParam @NotNull Integer status) {
        Auths.requireAdmin();
        couponService.updateStatus(couponId, status);
        return Result.success();
    }
}
