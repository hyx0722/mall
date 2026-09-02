package com.order.service;

import com.model.bean.Order;
import com.model.event.InventoryResultEvent;
import com.model.event.PaySuccessEvent;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.bean.SellerOrderVO;

import java.util.List;

public interface OrderService {

    List<Order> findAllOrder();

    Order findDetailOrder(Long id);

    /** 下单：本地事务写入订单+明细，提交后发布 order.created 事件 */
    Order createOrder(CreateOrderRequest request);

    /** 库存扣减成功回执：仅确认库存已锁定，订单保持待付款待支付 */
    void handleDeducted(InventoryResultEvent event);

    /** 库存扣减失败回执：待付款 -> 已取消 */
    void handleDeductFailed(InventoryResultEvent event);

    /** 支付成功回执：待付款 -> 待发货 */
    void handlePaid(PaySuccessEvent event);

    /** 定时任务：扫描超时未支付的待付款订单并自动取消（0 -> 4），取消后发 order.canceled 释放库存 */
    void cancelExpiredOrders(long minutes);

    // ---------- 用户/商家：手动取消与商家订单 ----------

    /** 买家取消自己的待付款订单（本人 + status=0，条件更新防并发），成功后发 order.canceled 释放库存 */
    void buyerCancel(Long userId, Long orderId);

    /** 商家：查看含自己商品的订单（每单带本人明细与可否取消标记） */
    List<SellerOrderVO> sellerOrders(Long userId);

    /** 商家取消某个待付款订单：仅当订单全部为本商家商品（混单不可取消），成功后发 order.canceled 释放库存 */
    void sellerCancel(Long userId, Long orderId);

    // ---------- 管理员：查看所有订单 ----------
    List<Order> adminFindOrders(Integer status);

    Order adminFindOrderById(Long id);

    List<OrderItem> adminFindOrderItems(Long orderId);
}
