package com.user.service;

import com.model.bean.PageBean;
import com.user.bean.StoreInfoVO;
import com.user.bean.StoreMessage;

import java.util.List;

/**
 * 商店订阅 + 商店公告。
 *
 * 「商店」在本仓就是**卖家用户**（商品靠 {@code product.user_id} 归属，店铺页是
 * {@code /store/:username}）。本接口的所有方法都**按 username 定位商店**，不收 storeId——
 * 店铺页拿不到卖家 id（{@code findOtherUserByUsername} 只查 username/avatar/status），
 * 服务端解析用户名即可，与 {@code CouponService.storeCoupons} 一致。
 *
 * 商家侧的三个方法（发/列/删公告）另从登录态取 sellerId，请求体里没有这个字段，
 * 客户端无从伪造成别家店铺的公告。
 */
public interface StoreService {

    // ---------- 买家 ----------

    /** 店铺页要的三个值（是否已订阅 / 订阅人数），一次往返拿全 */
    StoreInfoVO info(String username, Long viewerId);

    /**
     * 订阅。**幂等**：已订阅时返回成功而非报错（UI 会重复触发）。
     * 不允许订阅自己的店——自己给自己发通知没有意义。
     */
    void subscribe(String username, Long userId);

    /** 退订。**幂等**：未订阅时同样是成功 */
    void unsubscribe(String username, Long userId);

    /** 我订阅的商店的用户名列表 */
    List<String> mySubscriptions(Long userId);

    // ---------- 买卖双方都能看 ----------

    /**
     * 某店的公告列表。**不要求订阅**：公告在店铺页对所有人可见，
     * 订阅的价值在「主动推送」而不是「把公告藏起来」。
     */
    PageBean<StoreMessage> announcements(String username, Integer page, Integer size);

    // ---------- 商家 ----------

    /**
     * 发布公告：写 store_message + 群发通知给订阅者，同事务。
     * 只看登录态，任何登录用户都能给自己的店发公告（本仓卖家与买家不区分角色）。
     * 返回公告 id。
     */
    Long announce(Long sellerId, String content);

    /** 我发过的公告 */
    PageBean<StoreMessage> myAnnouncements(Long sellerId, Integer page, Integer size);

    /** 删除自己的公告。删不动别家的；已投递的通知不回收 */
    void deleteAnnouncement(Long sellerId, Long messageId);
}
