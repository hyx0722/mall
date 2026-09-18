package com.order.controller;

import com.mall.common.web.Auths;
import com.model.bean.Order;
import com.model.bean.Result;
import com.order.bean.OrderItem;
import com.order.bean.OrderRefund;
import com.order.bean.RefundAuditRequest;
import com.order.bean.Withdraw;
import com.order.bean.WithdrawAuditRequest;
import com.order.service.OrderAdminService;
import com.order.service.OrderRefundService;
import com.order.service.WithdrawService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员订单接口（内部系统，网关 /order/admin/** 前缀 + role=2 校验）。
 * 注意：必须带 @RestController，否则本类不会被注册为 Bean，所有映射都不会生效。
 */
@RestController
@Validated
public class OrderAdminController {

    @Autowired
    OrderAdminService orderAdminService;
    @Autowired
    OrderRefundService orderRefundService;
    @Autowired
    WithdrawService withdrawService;

    // ---------- 管理员：商家提现审核 ----------

    /** 待审核的提现申请 */
    @GetMapping("/admin/withdrawals")
    public Result<List<Withdraw>> adminWithdrawals() {
        Auths.requireAdmin();
        return Result.success(withdrawService.listPending());
    }

    /** 审核提现：通过即视为已打款（本仓未接真实出款通道），并把对应结算明细置为已提现 */
    @PostMapping("/admin/withdraw/audit")
    public Result adminWithdrawAudit(@RequestBody @Validated WithdrawAuditRequest request) {
        Auths.requireAdmin();
        withdrawService.audit(request.getWithdrawId(), Auths.currentUserId(),
                Boolean.TRUE.equals(request.getApprove()), request.getRejectReason());
        return Result.success();
    }

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

    // ---------- 管理员：退款申请审核（混单只能由管理员审核） ----------

    // 退款申请列表，可按审核状态过滤（refund_status：0待审核 1退款中 2已退款 3已驳回）
    @GetMapping("/admin/refunds")
    public Result<List<OrderRefund>> adminRefunds(@RequestParam(required = false) Integer status) {
        Auths.requireAdmin();
        return Result.success(orderRefundService.adminList(status));
    }

    // 审核退款申请：通过 -> 转 payment 打款；驳回 -> 订单回退到申请前状态
    @PostMapping("/admin/refund/audit")
    public Result adminRefundAudit(@RequestBody @Validated RefundAuditRequest request) {
        Auths.requireAdmin();
        orderRefundService.audit(Auths.currentUserId(), true, request.getRefundNo(),
                request.getApprove(), request.getRejectReason());
        return Result.success();
    }
}
