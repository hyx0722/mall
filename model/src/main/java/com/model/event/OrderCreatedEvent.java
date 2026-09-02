package com.model.event;

import lombok.Data;

import java.util.List;

/**
 * 订单已创建事件（order 服务 -> RabbitMQ -> inventory 服务）
 * 由 order 在本地事务提交后发布，inventory 消费后锁定/扣减库存。
 */
@Data
public class OrderCreatedEvent {

    /** 业务订单号 */
    private String orderNo;

    /** 订单主键 id（写库存流水时关联 orders.id） */
    private Long orderId;

    /** 下单用户 id */
    private Long userId;

    /** 订单明细（仅商品与数量） */
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
