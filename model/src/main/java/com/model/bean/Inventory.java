package com.model.bean;

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
@TableName("inventory")
public class Inventory {
    @TableField("id")
    @NotNull(message = "库存记录ID不能为空")
    private Integer id;
    @TableField("product_id")
    @NotNull(message = "商品ID不能为空")
    private Integer productId;
    @TableField("total_stock")
    private Integer totalStock;
    @TableField("locked_stock")
    private Integer lockedStock;
    @TableField("available_stock")
    private Integer availableStock;
    @TableField("sales_count")
    private Integer salesCount;
    @TableField("version")
    private Integer version;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;


}
