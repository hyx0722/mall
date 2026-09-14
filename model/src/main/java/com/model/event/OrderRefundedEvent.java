package com.model.event;

import lombok.Data;

import java.util.List;

/**
 * 订单已退款事件（order 服务 -> RabbitMQ -> inventory 服务）。
 *
 * order 消费 pay.refund.success 把订单置 6已退款时，与状态翻转同事务写入 outbox 后投递；
 * inventory 消费后把该订单占用的库存从 locked_stock 拨回 available_stock，
 * 并写一条 change_type=6（退货入库）流水。
 *
 * 与 {@link OrderCanceledEvent} 的区别：取消发生在支付前，释放的是「下单锁定」的库存；
 * 退款发生在支付后，回补的是「已成交后退货入库」的库存。二者流水类型不同
 * （4-释放锁定 / 6-退货入库），inventory_log 的 (order_id, product_id, change_type)
 * 唯一键让两条链路各自幂等、互不干扰。
 */
@Data
public class OrderRefundedEvent {

    /** 业务订单号 */
    private String orderNo;

    /** 订单主键 id */
    private Long orderId;

    /** 买家用户 id */
    private Long userId;

    /** 订单明细（仅商品与数量，供库存回补） */
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
