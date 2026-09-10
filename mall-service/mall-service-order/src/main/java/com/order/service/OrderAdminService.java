package com.order.service;

import com.model.bean.Order;
import com.order.bean.OrderItem;

import java.util.List;

public interface OrderAdminService {

    // ---------- 管理员：查看所有订单 ----------
    List<Order> adminFindOrders(Integer status);

    Order adminFindOrderById(Long id);

    List<OrderItem> adminFindOrderItems(Long orderId);
}
