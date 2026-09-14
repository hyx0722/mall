package com.product.controller;

import com.mall.common.web.Auths;
import com.model.bean.Result;
import com.product.bean.CartItem;
import com.product.bean.CartRequest;
import com.product.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车接口（买家，登录态；网关 /product/cart/** StripPrefix 后到本类）。
 * 用户身份一律取网关注入的登录态，不接受前端传 userId。
 */
@RestController
@Validated
public class CartController {

    @Autowired
    CartService cartService;

    /** 我的购物车（含实时价格与可购买标记） */
    @GetMapping("/cart/list")
    public Result<List<CartItem>> list() {
        Auths.requireLogin();
        return Result.success(cartService.list(Auths.currentUserId()));
    }

    /** 购物车商品种类数（导航角标） */
    @GetMapping("/cart/count")
    public Result<Integer> count() {
        Auths.requireLogin();
        return Result.success(cartService.count(Auths.currentUserId()));
    }

    /** 加购（已存在则累加），返回加购后的数量 */
    @PostMapping("/cart/add")
    public Result<Integer> add(@RequestBody @Validated CartRequest request) {
        Auths.requireLogin();
        return Result.success(cartService.add(Auths.currentUserId(),
                request.getProductId(), request.getQuantity()));
    }

    /** 覆盖数量（quantity<=0 即移除） */
    @PostMapping("/cart/update")
    public Result update(@RequestBody @Validated CartRequest request) {
        Auths.requireLogin();
        cartService.update(Auths.currentUserId(), request.getProductId(), request.getQuantity());
        return Result.success();
    }

    /** 移除单个商品 */
    @PostMapping("/cart/remove")
    public Result remove(@RequestBody @Validated CartRequest request) {
        Auths.requireLogin();
        cartService.remove(Auths.currentUserId(), request.getProductId());
        return Result.success();
    }

    /** 清空购物车 */
    @DeleteMapping("/cart/clear")
    public Result clear() {
        Auths.requireLogin();
        cartService.clear(Auths.currentUserId());
        return Result.success();
    }

    /** 结算成功后清理已下单的商品（前端在下单成功后调用） */
    @PostMapping("/cart/removeItems")
    public Result removeItems(@RequestBody List<Long> productIds) {
        Auths.requireLogin();
        cartService.removeItems(Auths.currentUserId(), productIds);
        return Result.success();
    }
}
