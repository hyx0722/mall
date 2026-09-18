package com.order.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 生成结算明细时用的行数据：订单明细 + 卖家（来自 product 的 user_id，跨库直读）。
 *
 * 单独建这个 DTO 而不是直接用 {@link OrderItem}：结算需要卖家 id，
 * 而 order_item 里没有（它是买家视角的快照），SellerId 必须从 product 库 join 出来。
 */
@Data
public class SettlementLine {

    private Long orderItemId;

    private Long productId;

    private Integer quantity;

    /** 行原价小计（order_item.total_price 的快照价） */
    private BigDecimal totalPrice;

    /** 卖家用户 id；商品已被删除时为 null */
    private Long sellerId;
}
