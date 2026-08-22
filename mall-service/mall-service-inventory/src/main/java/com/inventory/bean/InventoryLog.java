package com.inventory.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("inventory_log")
public class InventoryLog {
    @TableField("id")
    @NotNull(message = "流水ID不能为空")
    private Integer id;
    @TableField("product_id")
    @NotNull(message = "商品ID不能为空")
    private Integer productId;
    @TableField("order_id")
    private Integer orderId;
    @TableField("change_type")
    @NotNull(message = "变动类型不能为空")
    private Integer changeType;
    @TableField("change_quantity")
    @NotNull(message = "变动数量不能为空")
    private Integer changeQuantity;
    @TableField("before_total_stock")
    @NotNull(message = "变动前总库存不能为空")
    private Integer beforeTotalStock;
    @TableField("after_total_stock")
    @NotNull(message = "变动后总库存不能为空")
    private Integer afterTotalStock;
    @NotNull(message = "变动前锁定库存不能为空")
    @TableField("before_locked_stock")
    private Integer beforeLockedStock;
    @TableField("after_locked_stock")
    @NotNull(message = "变动后锁定库存不能为空")
    private Integer afterLockedStock;
    @TableField("remark")
    private String remark;
    @TableField("created_time")
    private LocalDateTime createdTime;

}
