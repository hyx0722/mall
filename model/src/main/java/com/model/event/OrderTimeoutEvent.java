package com.model.event;

import lombok.Data;

/**
 * 支付超时延迟标记（order 服务内部，经 mall.order.delay.exchange + per-message TTL
 * 延迟后死信回 mall.order.exchange/order.timeout 触发取消；对账扫表复用同一条取消逻辑）。
 */
@Data
public class OrderTimeoutEvent {

    /** 业务订单号 */
    private String orderNo;
}
