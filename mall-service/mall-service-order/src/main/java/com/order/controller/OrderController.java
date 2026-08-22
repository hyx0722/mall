package com.order.controller;

import com.model.bean.Order;
import com.model.bean.Result;
import com.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderController {
    @Autowired
    OrderService orderService;
    //查看自己所有的订单
    @GetMapping("findAllOrder")
    public Result<List<Order>> findAllOrder(){
        List<Order> allOrder = orderService.findAllOrder();
        return Result.success(allOrder);
    }
    //查看自己的某个详细订单
    @GetMapping("findDetailOrder")
    public Result<Order> findDetailOrder(Integer id){
        Order detailOrder = orderService.findDetailOrder(id);
        return Result.success(detailOrder);
    }
}
