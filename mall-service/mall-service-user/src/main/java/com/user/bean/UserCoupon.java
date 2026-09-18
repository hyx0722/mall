package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户持有的券。**券的归属在 user 服务**，但核销发生下单那一刻（order 服务），
 * 故 order 会同步调用本服务的核销接口，且核销与「折扣写进订单」在同一事务语义下完成。
 *
 * 每人每券限领 1 张，由 {@code uk_user_coupon(user_id, coupon_id)} 唯一键保证
 * （并发重复领取会撞键，而非靠「先查后插」）。
 *
 * 状态流转：0 未使用 --核销--> 1 已使用 --退券--> 0 未使用（取消 / 退款到账时）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_coupon")
public class UserCoupon {

    public static final int STATUS_UNUSED = 0;
    public static final int STATUS_USED = 1;
    public static final int STATUS_EXPIRED = 2;

    @TableField(value = "id")
    private Long id;

    @TableField(value = "user_id")
    private Long userId;

    @TableField(value = "coupon_id")
    private Long couponId;

    /** 0-未使用，1-已使用，2-已过期 */
    @TableField(value = "status")
    private Integer status;

    /** 核销的订单ID；未使用时为 NULL */
    @TableField(value = "order_id")
    private Long orderId;

    @TableField(value = "received_time")
    private LocalDateTime receivedTime;

    @TableField(value = "used_time")
    private LocalDateTime usedTime;

    /** 非持久化：联表带出的券定义，供「我的券」列表直接展示 */
    @TableField(exist = false)
    private Coupon coupon;
}
