package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板（券的定义）。用户持有的券见 {@link UserCoupon}。
 *
 * 满减与折扣共用本表，由 {@code couponType} 分派：
 * - 满减（1）：看 {@code thresholdAmount}（门槛）与 {@code discountAmount}（面额）；
 * - 折扣（2）：看 {@code discountRate}（如 0.850 = 8.5 折）与 {@code maxDiscountAmount}（封顶，可空）。
 * 另一个类型的字段保持在 ddl 默认值即可，计算时不被读取。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("coupon")
public class Coupon {

    public static final int TYPE_THRESHOLD = 1;  // 满减
    public static final int TYPE_DISCOUNT = 2;   // 折扣

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    /** 发券方为平台（管理员发）：券中心可领 */
    public static final long SELLER_PLATFORM = 0L;

    @TableField(value = "id")
    private Long id;

    /**
     * 发券商家ID；{@link #SELLER_PLATFORM} 表示平台券（管理员发）。
     *
     * 平台券在**券中心**可领；商家券只在**该商家的店铺页**可领。
     * 这个字段既决定展示位置，也是商家侧建券/启停时的归属校验依据。
     */
    @TableField(value = "seller_id")
    private Long sellerId;

    @TableField(value = "name")
    private String name;

    /** 1-满减，2-折扣 */
    @TableField(value = "coupon_type")
    private Integer couponType;

    /** 满减门槛：限定范围内小计须 >= 该值 */
    @TableField(value = "threshold_amount")
    private BigDecimal thresholdAmount;

    /** 满减面额 */
    @TableField(value = "discount_amount")
    private BigDecimal discountAmount;

    /** 折扣率，0.850 表示 8.5 折 */
    @TableField(value = "discount_rate")
    private BigDecimal discountRate;

    /** 折扣封顶金额；NULL 表示不封顶 */
    @TableField(value = "max_discount_amount")
    private BigDecimal maxDiscountAmount;

    @TableField(value = "total_count")
    private Integer totalCount;

    @TableField(value = "received_count")
    private Integer receivedCount;

    @TableField(value = "start_time")
    private LocalDateTime startTime;

    @TableField(value = "end_time")
    private LocalDateTime endTime;

    /** 1-启用，0-停用 */
    @TableField(value = "status")
    private Integer status;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;

    @TableField(value = "updated_time")
    private LocalDateTime updatedTime;
}
