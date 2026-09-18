package com.order.service.impl;

import com.model.bean.Order;
import com.model.enums.OrderStatus;
import com.model.enums.RefundAuditStatus;
import com.model.event.OrderRefundedEvent;
import com.model.event.PayRefundSuccessEvent;
import com.model.event.RefundRequestEvent;
import com.model.exception.BusinessException;
import com.order.bean.OrderItem;
import com.order.bean.OrderRefund;
import com.order.config.OrderRabbitConfig;
import com.order.mapper.OrderItemMapper;
import com.order.mapper.OrderMapper;
import com.order.mapper.OrderRefundMapper;
import com.order.service.OrderRefundService;
import com.mall.common.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 退款状态机实现（order 侧）。
 *
 * 与 {@link OrderCancelService} 一样是「事务漏斗」：每一次状态推进都与
 * refund.request / order.refunded 事件入 outbox 处于同一本地事务，
 * 保证「订单翻转了但事件没发出去」的窗口不存在。
 */
@Service
@Slf4j
public class OrderRefundServiceImpl implements OrderRefundService {

    @Autowired
    OrderMapper orderMapper;
    @Autowired
    OrderItemMapper orderItemMapper;
    @Autowired
    OrderRefundMapper orderRefundMapper;
    @Autowired
    OutboxService outboxService;

    @Override
    @Transactional
    public OrderRefund apply(Long userId, Long orderId, String reason) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 归属校验 + 取金额（金额一律取自订单，不接受前端传入）
        Order order = orderMapper.findDetailOrder(orderId, userId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        OrderStatus status = OrderStatus.of(order.getOrderStatus());
        if (status == null || !status.refundable()) {
            throw new BusinessException("订单当前状态不可申请退款");
        }
        // 条件更新 1/2/3 -> 5：越权、状态、并发重复申请三件事一并由 WHERE 兜住
        int affected = orderMapper.markRefunding(orderId, userId);
        if (affected == 0) {
            throw new BusinessException("订单当前状态不可申请退款");
        }

        OrderRefund refund = new OrderRefund();
        refund.setRefundNo(genRefundNo(userId));
        refund.setOrderId(orderId);
        refund.setOrderNo(order.getOrderNo());
        refund.setUserId(userId);
        refund.setRefundAmount(order.getTotalAmount());
        refund.setRefundReason(reason);
        orderRefundMapper.insertRefund(refund);
        log.info("[order] 买家 {} 对订单 {} 申请退款 refundNo={} amount={}",
                userId, orderId, refund.getRefundNo(), refund.getRefundAmount());

        // 同事务通知 payment 建退款单（等待审核，此时不涉及资金动作）
        RefundRequestEvent event = buildRequestEvent(RefundRequestEvent.ACTION_APPLY, refund, order, null);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_REFUND_REQUEST, null, event);
        return refund;
    }

    @Override
    public List<OrderRefund> myRefunds(Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return orderRefundMapper.selectByUserId(userId);
    }

    @Override
    public OrderRefund detailByOrderId(Long userId, Long orderId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 归属校验后返回（订单不属于自己时直接当作不存在，避免泄露他人退款信息）
        Order order = orderMapper.findDetailOrder(orderId, userId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return orderRefundMapper.selectLatestByOrderId(orderId);
    }

    @Override
    @Transactional
    public void audit(Long auditorId, boolean admin, String refundNo, boolean approve, String rejectReason) {
        if (auditorId == null) {
            throw new BusinessException("请先登录");
        }
        if (refundNo == null || refundNo.isBlank()) {
            throw new BusinessException("缺少退款单号");
        }
        OrderRefund refund = orderRefundMapper.selectByRefundNo(refundNo);
        if (refund == null) {
            throw new BusinessException("退款申请不存在");
        }
        if (refund.getRefundStatus() == null
                || refund.getRefundStatus() != RefundAuditStatus.PENDING.code()) {
            throw new BusinessException("该退款申请已被处理");
        }
        Order order = orderMapper.findOrderById(refund.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if (!admin) {
            // 卖家审核：整单商品必须都属于自己（混单只能由管理员审核，与商家整单取消同规矩）
            long mine = orderMapper.countMyItemLines(refund.getOrderId(), auditorId);
            long foreign = orderMapper.countForeignItemLines(refund.getOrderId(), auditorId);
            if (mine == 0) {
                throw new BusinessException("该订单中没有你的商品");
            }
            if (foreign > 0) {
                throw new BusinessException("订单含其他卖家的商品，需由管理员审核");
            }
        }

        if (approve) {
            int affected = orderRefundMapper.markApproved(refundNo, auditorId);
            if (affected == 0) {
                throw new BusinessException("该退款申请已被处理");
            }
            log.info("[order] 退款申请 {} 审核通过，转 payment 打款（审核人 {}）", refundNo, auditorId);
            RefundRequestEvent event = buildRequestEvent(RefundRequestEvent.ACTION_APPROVE, refund, order, null);
            outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_REFUND_REQUEST, null, event);
            return;
        }

        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BusinessException("驳回时必须填写驳回原因");
        }
        int affected = orderRefundMapper.markRejected(refundNo, auditorId, rejectReason);
        if (affected == 0) {
            throw new BusinessException("该退款申请已被处理");
        }
        // 订单退款中 -> 申请前状态（由 shipping_status 反推）
        orderMapper.revertRefunding(refund.getOrderId());
        log.info("[order] 退款申请 {} 被驳回（审核人 {}），订单 {} 已回退", refundNo, auditorId, refund.getOrderId());
        RefundRequestEvent event = buildRequestEvent(RefundRequestEvent.ACTION_REJECT, refund, order, rejectReason);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_REFUND_REQUEST, null, event);
    }

    @Override
    public List<OrderRefund> sellerPending(Long sellerId) {
        if (sellerId == null) {
            throw new BusinessException("请先登录");
        }
        return orderRefundMapper.selectPendingForSeller(sellerId);
    }

    @Override
    public List<OrderRefund> adminList(Integer status) {
        return orderRefundMapper.selectForAdmin(status);
    }

    @Override
    @Transactional
    public void handleRefundSuccess(PayRefundSuccessEvent event) {
        if (event == null || event.getOrderId() == null) {
            return;
        }
        // 条件更新 5 -> 6：渠道重复回调/事件重投时拿到 0 行，直接幂等返回不再发库存回补
        int affected = orderMapper.markRefunded(event.getOrderId());
        if (affected == 0) {
            log.info("[order] 订单 {} 非退款中，跳过重复的退款到账处理", event.getOrderId());
            return;
        }
        orderRefundMapper.markRefundedByOrderId(event.getOrderId());

        Order order = orderMapper.findOrderById(event.getOrderId());
        if (order == null) {
            log.warn("[order] 退款到账后查不到订单 {}", event.getOrderId());
            return;
        }
        // 同事务登记库存回补事件：退款到账与「通知库存回补」原子
        OrderRefundedEvent refunded = new OrderRefundedEvent();
        refunded.setOrderNo(order.getOrderNo());
        refunded.setOrderId(order.getId());
        refunded.setUserId(order.getUserId());
        List<OrderRefundedEvent.Item> items = new ArrayList<>();
        for (OrderItem item : orderItemMapper.selectByOrderId(order.getId())) {
            OrderRefundedEvent.Item it = new OrderRefundedEvent.Item();
            it.setProductId(item.getProductId());
            it.setQuantity(item.getQuantity());
            items.add(it);
        }
        refunded.setItems(items);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_ORDER_REFUNDED, null, refunded);
        log.info("[order] 订单 {} 退款到账 -> 已退款，已登记库存回补事件", order.getOrderNo());
    }

    private RefundRequestEvent buildRequestEvent(String action, OrderRefund refund, Order order, String rejectReason) {
        RefundRequestEvent event = new RefundRequestEvent();
        event.setAction(action);
        event.setRefundNo(refund.getRefundNo());
        event.setOrderNo(refund.getOrderNo());
        event.setOrderId(refund.getOrderId());
        event.setUserId(order.getUserId());
        event.setRefundAmount(refund.getRefundAmount());
        event.setRefundReason(refund.getRefundReason());
        event.setRejectReason(rejectReason);
        return event;
    }

    /** 退款单号：RF + 时间戳 + 4 位随机 + 4 位用户尾号（与订单号同款生成方式） */
    private String genRefundNo(Long userId) {
        return "RF" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", userId % 10000);
    }
}
