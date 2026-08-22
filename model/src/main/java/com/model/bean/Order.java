package com.model.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @TableField("id")
    @NotNull(message = "订单ID不能为0")
    private Integer id;
    @TableField("order_no")
    @NotNull(message = "订单编号（业务唯一）不能为0")
    private String orderNo;
    @TableField("user_id")
    @NotNull(message = "买家用户ID不能为0")
    private Integer userId;
    @TableField("address_id")
    private Integer addressId;
    @TableField("total_amount")
    private BigDecimal totalAmount;
    @TableField("discount_amount")
    private BigDecimal discountAmount;
    @TableField("order_status")
    private Integer orderStatus;
    @TableField("shipping_status")
    private Integer shippingStatus;
    @TableField("shipping_time")
    private LocalDateTime shippingTime;
    @TableField("complete_time")
    private LocalDateTime completeTime;
    @TableField("cancel_time")
    private LocalDateTime cancelTime;
    @TableField("receiver_name")
    private String receiverName;
    @TableField("receiver_phone")
    private String receiverPhone;
    @TableField("receiver_address")
    private String receiverAddress;
    @TableField("remark")
    private String remark;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;

}
