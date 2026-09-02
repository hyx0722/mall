package com.order.service;

import com.model.bean.Order;
import com.model.event.InventoryResultEvent;
import com.order.bean.CreateOrderRequest;

import java.util.List;

public interface OrderService {

    List<Order> findAllOrder();

    Order findDetailOrder(Integer id);

    /** 下单：本地事务写入订单+明细，提交后发布 order.created 事件 */
    Order createOrder(CreateOrderRequest request);

    /** 库存扣减成功回执：待付款 -> 待发货 */
    void handleDeducted(InventoryResultEvent event);

    /** 库存扣减失败回执：待付款 -> 已取消 */
    void handleDeductFailed(InventoryResultEvent event);
}
