package com.model.event;

import lombok.Data;

import java.util.List;

/**
 * 订单已取消事件（order 服务 -> RabbitMQ -> inventory/payment 服务）
 * 由 order 在「支付超时自动取消」落库后发布：
 *  - inventory 消费后释放该订单锁定的库存（写 change_type=4 流水）；
 *  - payment 消费后关闭该订单仍待支付的支付单。
 */
@Data
public class OrderCanceledEvent {

    /** 业务订单号 */
    private String orderNo;

    /** 订单主键 id */
    private Long orderId;

    /** 下单用户 id */
    private Long userId;

    /** 订单明细（仅商品与数量，供库存释放） */
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
