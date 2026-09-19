package com.order.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 买家视角的订单明细行 + 该行能否评价 / 已评价（订单详情页用）。
 *
 * <p>补的是一个**既有缺口**：买家侧此前没有任何接口能拿到自己订单的明细行——
 * {@code findDetailOrder} 只返回 orders 头，{@code /admin/findOrderItems} 是管理员的，
 * {@code SellerOrderVO.items} 是卖家的（且只含本卖家商品）。
 *
 * <p>字段全部来自 {@code order_item} 的**下单快照**，不 join 商品库：
 * 商品被改名或下架后，订单里显示的应当仍是当时买的东西。
 */
@Data
public class BuyerOrderItemVO {

    private Long productId;

    private String productName;

    private String productImage;

    private BigDecimal productPrice;

    private Integer quantity;

    private BigDecimal totalPrice;

    private LocalDateTime createdTime;

    /**
     * 本行能否评价：订单已完成、且这一行还没被评价过。
     * 前端据此决定是否渲染「评价」按钮。
     */
    private boolean canReview;

    /**
     * 已评价时带出评价 id，否则为 null。
     * 前端据此渲染「查看评价」而不是「评价」。
     */
    private Long reviewId;
}
