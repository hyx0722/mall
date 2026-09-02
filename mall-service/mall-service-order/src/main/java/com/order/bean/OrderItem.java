package com.order.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_item")
public class OrderItem {
    @TableField("id")
    @NotNull(message = "明细ID不能为空")
    private Long id;
    @TableField("order_id")
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    @TableField("product_id")
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    @TableField("product_name")
    @NotNull(message = "商品名称（快照）不能为空")
    private String productName;
    @TableField("product_image")
    private String productImage;
    @TableField("product_price")
    @NotNull(message = "商品单价（快照）不能为空")
    private BigDecimal productPrice;
    @TableField("quantity")
    private Integer quantity;
    @TableField("total_price")
    private BigDecimal totalPrice;
    @TableField("created_time")
    private LocalDateTime createdTime;

}
