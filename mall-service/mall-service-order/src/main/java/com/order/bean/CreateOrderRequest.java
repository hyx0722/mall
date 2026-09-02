package com.order.bean;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单请求：商品明细（productId + quantity），地址与备注可选。
 */
@Data
public class CreateOrderRequest {

    /** 收货地址 id（可选，快照在支付/发货阶段再补） */
    private Integer addressId;

    private String remark;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "商品id不能为空")
        private Integer productId;

        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量至少为1")
        private Integer quantity;
    }
}
