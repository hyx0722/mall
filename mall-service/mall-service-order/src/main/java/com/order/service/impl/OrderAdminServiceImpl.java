package com.order.service.impl;

import com.model.bean.Order;
import com.order.bean.OrderItem;
import com.order.mapper.OrderAdminMapper;
import com.order.service.OrderAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class OrderAdminServiceImpl implements OrderAdminService {

    @Autowired
    OrderAdminMapper orderAdminMapper;

    @Override
    public List<Order> adminFindOrders(Integer status) {
        return orderAdminMapper.findAdminOrders(status);
    }

    @Override
    public Order adminFindOrderById(Long id) {
        return orderAdminMapper.findOrderById(id);
    }

    @Override
    public List<OrderItem> adminFindOrderItems(Long orderId) {
        return orderAdminMapper.selectDetailByOrderId(orderId);
    }
}
