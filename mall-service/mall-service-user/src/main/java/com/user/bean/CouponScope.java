package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 优惠券的适用范围：一个券可挂多条（商品 与 分类 可混挂）。
 *
 * 语义约定：**没有本表记录的券视为全场通用**。
 * 判定在 order 服务下单时进行——它会用商品快照里的 productId / categoryId 与本表比对。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("coupon_scope")
public class CouponScope {

    public static final int TYPE_PRODUCT = 1;
    public static final int TYPE_CATEGORY = 2;

    @TableField(value = "id")
    private Long id;

    @TableField(value = "coupon_id")
    private Long couponId;

    /** 1-商品，2-分类 */
    @TableField(value = "scope_type")
    private Integer scopeType;

    /** 商品ID 或 分类ID，依 scopeType 解释 */
    @TableField(value = "scope_id")
    private Long scopeId;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;
}
