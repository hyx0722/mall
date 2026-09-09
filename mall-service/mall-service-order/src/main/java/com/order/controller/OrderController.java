package com.order.controller;

import com.mall.common.web.Auths;
import com.model.bean.Order;
import com.model.bean.Result;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.bean.SellerOrderVO;
import com.order.bean.ShipRequest;
import com.order.bean.Shipping;
import com.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    // ---------- 买家/商家：手动取消与商家订单（登录即可） ----------

    // 买家取消自己的待付款订单：释放锁定库存 + 关闭未付支付单（发 order.canceled）
    @PostMapping("cancel")
    public Result cancel(@RequestParam Long id) {
        Auths.requireLogin();
        orderService.buyerCancel(Auths.currentUserId(), id);
        return Result.success();
    }

    // 商家：查看含自己商品的订单（每单带本人明细与可否取消标记）
    @GetMapping("/seller/orders")
    public Result<List<SellerOrderVO>> sellerOrders() {
        Auths.requireLogin();
        return Result.success(orderService.sellerOrders(Auths.currentUserId()));
    }

    // 商家取消某个待付款订单（仅限该订单全部为本商家商品，发 order.canceled 释放库存）
    @PostMapping("/seller/cancel")
    public Result sellerCancel(@RequestParam Long id) {
        Auths.requireLogin();
        orderService.sellerCancel(Auths.currentUserId(), id);
        return Result.success();
    }

    // ---------- 发货 / 确认收货（本次补全的状态机下半段） ----------

    // 商家对自己商品所属订单发货；全部卖家都发货后整单 1待发货 -> 2待收货
    @PostMapping("/seller/ship")
    public Result sellerShip(@RequestBody @Validated ShipRequest request) {
        Auths.requireLogin();
        orderService.sellerShip(Auths.currentUserId(), request.getOrderId(),
                request.getLogisticsCompany(), request.getTrackingNo(), request.getRemark());
        return Result.success();
    }

    // 买家确认收货：整单已发货(2待收货) -> 3已完成
    @PostMapping("/receive")
    public Result receive(@RequestParam Long id) {
        Auths.requireLogin();
        orderService.buyerReceive(Auths.currentUserId(), id);
        return Result.success();
    }

    // 买家查看某订单的物流发货单（归属校验后返回）
    @GetMapping("/shippings")
    public Result<List<Shipping>> shippings(@RequestParam Long orderId) {
        Auths.requireLogin();
        return Result.success(orderService.listShippings(Auths.currentUserId(), orderId));
    }

}
