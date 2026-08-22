package com.order.service;

import com.model.bean.Order;

import java.util.List;

public interface OrderService {

    List<Order> findAllOrder();

    Order findDetailOrder(Integer id);
}
