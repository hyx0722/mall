package com.model.event;

import lombok.Data;

/**
 * 库存处理结果事件（inventory 服务 -> RabbitMQ -> order 服务）
 * 通过不同 routing key 区分扣减成功(inventory.deducted)与失败(inventory.deduct_failed)。
 */
@Data
public class InventoryResultEvent {

    /** 业务订单号 */
    private String orderNo;
}
