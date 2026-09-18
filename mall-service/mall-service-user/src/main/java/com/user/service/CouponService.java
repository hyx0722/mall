package com.user.service;

import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.user.bean.Coupon;
import com.user.bean.CouponCreateRequest;
import com.user.bean.UsableCouponVO;
import com.user.bean.UserCoupon;

import java.util.List;

/**
 * 优惠券服务。券的**定义与归属**都在 user 服务，但**核销**发生在下单那一刻，
 * 由 order 服务同步调用 {@link #preview} 与 {@link #use}。
 *
 * 可用性规则（门槛、有效期、指定商品/分类）**只在本实现里写一份**，
 * order 侧不复制任何判定，只负责把商品快照明细传过来并采用返回的抵扣额。
 */
public interface CouponService {

    // ---------- 买家 ----------

    /** 券中心：启用中、在有效期内、尚有余量的券 */
    List<Coupon> center();

    /** 领券。并发抢券靠条件 UPDATE，每人每券限领 1 张靠唯一键 */
    void receive(Long userId, Long couponId);

    /** 我的券（status 为 null 表示全部），已批量补全券定义。背包页即以此为数据源 */
    List<UserCoupon> mine(Long userId, Integer status);

    /** 店铺页：某商家（按用户名定位）当前可领的券 */
    List<Coupon> storeCoupons(String username);

    // ---------- 供 order 服务调用（下单链路） ----------

    /** 试算：只读，不改任何状态。不可用时返回 usable=false + reason */
    CouponPreviewResult preview(Long userId, CouponPreviewRequest request);

    /** 结算页的可用券列表：对本单一并评估用户所有未使用的券，可用的排前面并按抵扣额降序 */
    List<UsableCouponVO> usableCoupons(Long userId, List<CouponPreviewRequest.Line> lines);

    /** 核销（下单事务内调用，失败须让下单整体回滚） */
    void use(Long userId, Long userCouponId, Long orderId);

    /**
     * 退券：**只由 {@code CouponReleaseListener} 消费 order.canceled / order.refunded 时调用**，
     * 没有对外接口（暴露出去等于允许买家「用完再要回来」重复抵扣）。
     * 条件 UPDATE，重复投递无副作用。
     */
    void releaseByOrderId(Long orderId);

    // ---------- 商家（券只对自己的商品生效，只在自家店铺页可领） ----------

    /**
     * 商家建券。与管理员建券的两点差别：
     * 必须指定至少一个**自己的**商品（逐个向商品服务校验归属），且只允许「指定商品」范围——
     * 分类是全平台共享的，放开等于变相全场发券。
     */
    void createForSeller(Long sellerId, CouponCreateRequest request);

    /** 商家自己的券（含停用与已领完），供商家中心管理 */
    List<Coupon> sellerCoupons(Long sellerId);

    /** 商家启停自己的券；条件 UPDATE 带 seller_id，改不动别人的券 */
    void updateStatusForSeller(Long sellerId, Long couponId, Integer status);

    // ---------- 管理员（平台券） ----------

    void create(CouponCreateRequest request);

    void updateStatus(Long couponId, Integer status);

    List<Coupon> listAll(String keyword);
}
