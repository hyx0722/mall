package com.user.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 店铺页顶部要的三个值，一次往返拿全。
 *
 * 之所以是一个聚合 VO 而不是三个接口：店铺页每次挂载都要这三样，
 * 分开就是三次往返且中间状态不一致（订阅数已变、按钮还是旧态）。
 *
 * ⚠️ 所有 {@code /store/*} 接口都**按 username 定位**，不收 storeId：
 * 店铺页路由是 {@code /store/:username}，而 {@code UserMapper.findOtherUserByUsername}
 * 只查 username/avatar/status，前端根本拿不到卖家 id。服务端解析用户名即可，
 * 与 {@code CouponServiceImpl.storeCoupons} 的做法一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoreInfoVO {

    private String username;

    /** 当前登录用户是否已订阅；未登录时为 false */
    private boolean subscribed;

    /** 该店订阅人数 */
    private long subscriberCount;
}
