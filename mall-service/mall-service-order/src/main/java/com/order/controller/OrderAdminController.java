package com.order.controller;

import com.mall.common.web.Auths;
import com.model.bean.Order;
import com.model.bean.Result;
import com.order.bean.OrderItem;
import com.order.service.OrderAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public class OrderAdminController {

    @Autowired
    OrderAdminService orderAdminService;

    // ---------- 管理员：查看所有订单（内部系统，/order/admin/*） ----------

    // 所有订单，可按订单状态过滤（order_status：0待付款 1待发货 2待收货 3已完成 4已取消 5退款中 6已退款）
    @GetMapping("/admin/findAllOrder")
    public Result<List<Order>> adminFindAllOrder(@RequestParam(required = false) Integer status) {
        Auths.requireAdmin();
        return Result.success(orderAdminService.adminFindOrders(status));
    }

    // 任意订单头
    @GetMapping("/admin/findDetailOrder")
    public Result<Order> adminFindDetailOrder(Long id) {
        Auths.requireAdmin();
        Order order = orderAdminService.adminFindOrderById(id);
        return order == null ? Result.error("订单不存在") : Result.success(order);
    }

    // 订单明细（商品名/图/价/数量）
    @GetMapping("/admin/findOrderItems")
    public Result<List<OrderItem>> adminFindOrderItems(Long orderId) {
        Auths.requireAdmin();
        return Result.success(orderAdminService.adminFindOrderItems(orderId));
    }
}
