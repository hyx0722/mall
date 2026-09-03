package com.order.bean;

import com.model.bean.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商家视角的订单视图：订单头（含买家名 + 收货快照）+ 该订单中属于本商家的明细
 * + 是否可取消 + 本商家的发货单（null=本商家尚未发货）。
 * cancellable：待付款(0) 且 该订单不含其它卖家的商品时商家才能整单取消。
 * shipInfo：订单处于待发货(1) 且本商家未发货时可操作「发货」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellerOrderVO {

    /** 订单头（buyerName 已联表带出；receiver_* 为支付时冻结的收货快照） */
    private Order order;

    /** 属于本商家的明细行（混单时不含他人商品行） */
    private List<OrderItem> items;

    /** 本商家可否取消此单 */
    private boolean cancellable;

    /** 本商家的发货单（null=本商家尚未发货；uk_order_seller 保证每单每卖家至多一条） */
    private Shipping shipInfo;
}
