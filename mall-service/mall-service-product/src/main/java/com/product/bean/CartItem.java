package com.product.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目（回给前端的视图对象）。
 *
 * Redis 里只存 productId -> quantity 这一对最小事实，名称/价格/主图在读取时
 * 从 product 表实时补全——这样商家改价后购物车立刻反映新价，不存在"购物车里
 * 存了一份过期价格"的问题；下单时 order 服务也会重新拉一次商品快照，
 * 最终成交价永远以那一刻的商品表为准。
 */
@Data
public class CartItem {

    private Long productId;

    /** 加购数量 */
    private Integer quantity;

    /** 商品名（商品已删除时为 null） */
    private String name;

    private String subtitle;

    private String mainImage;

    /** 当前单价（实时取自商品表） */
    private BigDecimal price;

    /** 商品状态：1-在售，0-已下架（下架商品保留在车里但不可结算） */
    private Integer status;

    /** 卖家用户 id（购物车按卖家分组展示用） */
    private Long sellerId;

    /** 小计 = 单价 × 数量；商品已删除时为 null */
    private BigDecimal subtotal;

    /** 商品是否仍可购买（存在且在售） */
    private Boolean available;
}
