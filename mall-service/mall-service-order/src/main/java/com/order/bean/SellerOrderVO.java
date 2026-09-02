package com.order.bean;

import com.model.bean.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商家视角的订单视图：订单头（含买家名）+ 该订单中属于本商家的明细 + 是否可取消。
 * cancellable：待付款(0) 且 该订单不含其它卖家的商品时商家才能整单取消。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerOrderVO {

    /** 订单头（buyerName 已联表带出） */
    private Order order;

    /** 属于本商家的明细行（混单时不含他人商品行） */
    private List<OrderItem> items;

    /** 本商家可否取消此单 */
    private boolean cancellable;
}
