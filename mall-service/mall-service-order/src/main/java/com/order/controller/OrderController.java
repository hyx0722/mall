package com.order.controller;

import com.model.bean.Order;
import com.model.bean.Result;
import com.order.bean.CreateOrderRequest;
import com.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
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
    public Result<Order> findDetailOrder(Long id){
        Order detailOrder = orderService.findDetailOrder(id);
        return Result.success(detailOrder);
    }

    //下单：写入订单+明细，异步经 RabbitMQ 由库存服务扣减库存并回执状态
    @PostMapping("createOrder")
    public Result<Order> createOrder(@RequestBody @Validated CreateOrderRequest request){
        Order order = orderService.createOrder(request);
        return Result.success(order);
    }
}
