package com.user.controller;

import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Result;
import com.user.bean.MessageCreateRequest;
import com.user.bean.StoreInfoVO;
import com.user.bean.StoreMessage;
import com.user.service.StoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商店订阅与商店公告。对外路径是 {@code /user/store/*}（网关 StripPrefix 掉 /user）。
 *
 * <p><b>为什么全都按 username 而不是 storeId？</b>
 * 店铺页路由是 {@code /store/:username}，而 {@code UserMapper.findOtherUserByUsername}
 * 只查 username/avatar/status，前端**根本拿不到卖家 id**。所以这些接口一律收用户名、
 * 由服务端解析成 storeId，与 {@code CouponService.storeCoupons} 的做法一致。
 *
 * <p><b>归属来源</b>：发公告的 sellerId、订阅的 userId 全部取自登录态，请求体里没有这两个字段
 * （{@link MessageCreateRequest} 只有 content），客户端无从伪造成别家店铺的公告。
 */
@RestController
@Validated
public class StoreController {

    @Autowired
    StoreService storeService;

    // ---------- 买家 ----------

    /** 店铺页要的三个值：是否已订阅 / 订阅人数。未登录也能看（subscribed 恒为 false） */
    @GetMapping("/store/status")
    public Result<StoreInfoVO> status(@RequestParam String username) {
        return Result.success(storeService.info(username, Auths.currentUserId()));
    }

    @PostMapping("/store/subscribe")
    public Result subscribe(@RequestParam String username) {
        Auths.requireLogin();
        storeService.subscribe(username, Auths.currentUserId());
        return Result.success();
    }

    @PostMapping("/store/unsubscribe")
    public Result unsubscribe(@RequestParam String username) {
        Auths.requireLogin();
        storeService.unsubscribe(username, Auths.currentUserId());
        return Result.success();
    }

    /** 我订阅的商店用户名列表 */
    @GetMapping("/store/my")
    public Result<List<String>> my() {
        Auths.requireLogin();
        return Result.success(storeService.mySubscriptions(Auths.currentUserId()));
    }

    /**
     * 某店的公告（店铺页展示）。
     * **不要求订阅**——公告在店铺页对所有人可见，订阅的价值在推送而不是把公告藏起来。
     */
    @GetMapping("/store/message/list")
    public Result<PageBean<StoreMessage>> announcements(
            @RequestParam String username,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(storeService.announcements(username, page, size));
    }

    // ---------- 商家 ----------

    /** 发布公告：落库 + 群发给订阅者（同事务）。任何登录用户都能给自己的店发公告 */
    @PostMapping("/store/seller/message/create")
    public Result<Long> create(@RequestBody @Valid MessageCreateRequest request) {
        Auths.requireLogin();
        return Result.success(storeService.announce(Auths.currentUserId(), request.getContent().trim()));
    }

    /** 我发过的公告 */
    @GetMapping("/store/seller/message/list")
    public Result<PageBean<StoreMessage>> myMessages(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Auths.requireLogin();
        return Result.success(storeService.myAnnouncements(Auths.currentUserId(), page, size));
    }

    /** 删除自己的公告。已投递的通知不回收 */
    @DeleteMapping("/store/seller/message/delete")
    public Result deleteMessage(@RequestParam @NotNull Long id) {
        Auths.requireLogin();
        storeService.deleteAnnouncement(Auths.currentUserId(), id);
        return Result.success();
    }
}
