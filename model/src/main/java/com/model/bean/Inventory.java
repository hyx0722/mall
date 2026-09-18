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
    private Long id;
    @TableField("product_id")
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    @TableField("user_id")
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @TableField("total_stock")
    private Integer totalStock;
    @TableField("locked_stock")
    private Integer lockedStock;
    @TableField("available_stock")
    private Integer availableStock;
    @TableField("sales_count")
    private Integer salesCount;

    /**
     * 库存预警阈值：{@code available_stock <= warnThreshold} 即告警；0 表示不预警。
     * DDL 里带 {@code DEFAULT 0} 是硬要求——见 inventory.sql 的注释，否则并发测试会失败。
     */
    @TableField("warn_threshold")
    private Integer warnThreshold;

    /** 上次告警时间：冷却窗口用，避免每轮扫描都重复告警 */
    @TableField("last_warn_time")
    private LocalDateTime lastWarnTime;
    @TableField("version")
    private Integer version;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
    // 非持久化：管理员库存列表联表带出的商品名/卖家用户名
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String sellerName;

}
