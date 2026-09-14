package com.order.service;

import com.model.event.PayRefundSuccessEvent;
import com.order.bean.OrderRefund;

import java.util.List;

/**
 * 退款申请与审核（order 侧是退款状态机的主人）。
 *
 * 链路：
 *   买家 apply          1/2/3 -> 5退款中，落 order_refund(待审核)，发 refund.request(APPLY)
 *   卖家/管理员 audit   通过 -> order_refund 置退款中，发 refund.request(APPROVE) 转 payment 打款
 *                       驳回 -> order_refund 置已驳回，订单 5 -> 申请前状态，发 refund.request(REJECT)
 *   payment 打款成功    -> 消费 pay.refund.success：订单 5 -> 6已退款，发 order.refunded 供库存回补
 */
public interface OrderRefundService {

    /** 买家申请退款（整单全额）：订单 待发货/待收货/已完成 -> 退款中，并登记退款申请单 */
    OrderRefund apply(Long userId, Long orderId, String reason);

    /** 买家：我的全部退款申请 */
    List<OrderRefund> myRefunds(Long userId);

    /** 买家：某订单的退款申请（归属校验；无申请返回 null） */
    OrderRefund detailByOrderId(Long userId, Long orderId);

    /**
     * 审核退款申请。
     * @param admin true=管理员（可审任意订单）；false=卖家（仅限整单商品都属于自己）
     */
    void audit(Long auditorId, boolean admin, String refundNo, boolean approve, String rejectReason);

    /** 卖家：待自己审核的退款申请（仅整单属于自己的订单） */
    List<OrderRefund> sellerPending(Long sellerId);

    /** 管理员：退款申请列表（status 为 null 时返回全部） */
    List<OrderRefund> adminList(Integer status);

    /** 退款到账回执：订单 5退款中 -> 6已退款，并登记 order.refunded 事件供库存回补 */
    void handleRefundSuccess(PayRefundSuccessEvent event);
}
