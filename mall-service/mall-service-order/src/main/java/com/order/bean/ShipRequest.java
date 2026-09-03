package com.order.bean;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商家发货请求：指定订单 id + 物流信息（物流公司/单号/备注可空）。
 */
@Data
public class ShipRequest {

    @NotNull(message = "订单 id 不能为空")
    private Long orderId;

    /** 物流公司（可空） */
    private String logisticsCompany;

    /** 物流单号（可空） */
    private String trackingNo;

    /** 发货备注（可空） */
    private String remark;
}
