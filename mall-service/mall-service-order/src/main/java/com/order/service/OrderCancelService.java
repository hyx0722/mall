package com.order.service;

import com.model.bean.Order;
import com.model.event.OrderCanceledEvent;
import com.order.bean.OrderItem;
import com.order.config.OrderRabbitConfig;
import com.order.mapper.OrderItemMapper;
import com.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单取消的统一事务漏斗：所有「把订单取消并对外发布 order.canceled」的路径都走这里，
 * 保证「0待付款 -> 4已取消」的落库与 order.canceled 事件入 outbox 在同一事务（根治发布非原子）。
 * 调用方：支付超时延迟消费、对账扫表、库存扣减失败回执、买家/商家手动取消。
 */
@Service
@Slf4j
public class OrderCancelService {

    @Autowired
    OrderMapper orderMapper;
    @Autowired
    OrderItemMapper orderItemMapper;
    @Autowired
    OutboxService outboxService;

    /**
     * 按订单号条件取消（仅待付款 0->4）+ 同事务把 order.canceled 写入 outbox。
     * 已非待付款/不存在时静默返回（幂等，延迟标记与对账扫表并发安全）。
     */
    @Transactional
    public void cancelByOrderNo(String orderNo) {
        if (orderNo == null) {
            return;
        }
        int affected = orderMapper.markCancelled(orderNo); // 0->4 条件更新
        if (affected == 0) {
            log.info("[order] 订单 {} 非待付款或已取消，跳过超时取消", orderNo);
            return;
        }
        Order order = orderMapper.selectByOrderNo(orderNo);
        enqueueCanceled(order);
        log.info("[order] 订单 {} 已取消并登记 order.canceled 事件", orderNo);
    }

    /** 手动取消（买家/商家已完成归属+状态校验并条件翻转后）：同事务补发 order.canceled 事件。 */
    @Transactional
    public void enqueueCanceledForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        Order order = orderMapper.findOrderById(orderId);
        if (order == null) {
            return;
        }
        enqueueCanceled(order);
    }

    private void enqueueCanceled(Order order) {
        OrderCanceledEvent event = buildCanceledEvent(order);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_ORDER_CANCELED, null, event);
    }

    private OrderCanceledEvent buildCanceledEvent(Order order) {
        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        OrderCanceledEvent event = new OrderCanceledEvent();
        event.setOrderNo(order.getOrderNo());
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        List<OrderCanceledEvent.Item> evtItems = new ArrayList<>();
        for (OrderItem item : items) {
            OrderCanceledEvent.Item it = new OrderCanceledEvent.Item();
            it.setProductId(item.getProductId());
            it.setQuantity(item.getQuantity());
            evtItems.add(it);
        }
        event.setItems(evtItems);
        return event;
    }
}
