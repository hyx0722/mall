package com.order.service.impl;

import com.model.bean.Order;
import com.model.util.ThreadLocalUtil;
import com.order.mapper.OrderMapper;
import com.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    OrderMapper orderMapper;

    @Override
    public List<Order> findAllOrder() {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return orderMapper.findAllOrder(userId);
    }

    @Override
    public Order findDetailOrder(Integer id) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return orderMapper.findDetailOrder(id,userId);
    }
}
