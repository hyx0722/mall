package com.order.service;

import com.model.bean.Order;
import com.model.event.InventoryResultEvent;
import com.model.event.PaySuccessEvent;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.bean.SellerOrderVO;
import com.order.bean.Shipping;

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

    /** 支付成功回执：待付款 -> 待发货，并冻结收货人快照 */
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

    // ---------- 发货/收货（本次补全的状态机下半段） ----------

    /**
     * 商家对自己商品所属订单发货：写入发货单；若该单全部卖家都已发货则整单 1待发货 -> 2待收货。
     * 事务内首条语句锁定订单行，串行化同一订单的并发发货，避免「两个最后一卖都判断为未齐」卡死订单。
     */
    void sellerShip(Long sellerId, Long orderId, String logisticsCompany, String trackingNo, String remark);

    /** 买家确认收货：整单已发货(2待收货) -> 3已完成（归属 + status=2 条件更新防重） */
    void buyerReceive(Long userId, Long orderId);

    /** 买家查看某订单的物流发货单（归属校验后返回，可为空列表） */
    List<Shipping> listShippings(Long userId, Long orderId);

    // ---------- 管理员：查看所有订单 ----------
    List<Order> adminFindOrders(Integer status);

    Order adminFindOrderById(Long id);

    List<OrderItem> adminFindOrderItems(Long orderId);
}
