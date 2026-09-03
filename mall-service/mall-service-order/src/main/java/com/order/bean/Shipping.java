package com.order.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发货单：每「订单 + 卖家」一条，卖家对自己商品所属订单发货后生成。
 * 支持混单（一单含多卖家）各卖家分开发货；同一卖家对一个订单至多一条（uk_order_seller 约束）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("shipping")
public class Shipping {
    @TableField("id")
    private Long id;
    @TableField("ship_no")
    @NotNull(message = "发货单号不能为空")
    private String shipNo;
    @TableField("order_id")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    @TableField("seller_id")
    @NotNull(message = "卖家用户ID不能为空")
    private Long sellerId;
    @TableField("logistics_company")
    private String logisticsCompany;
    @TableField("tracking_no")
    private String trackingNo;
    @TableField("remark")
    private String remark;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
