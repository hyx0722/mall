package com.product.bean;

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
@TableName("category")
public class category {
    @TableField("id")
    @NotNull(message = "分类ID不为空")
    private Integer id;
    @TableField("parent_id")
    private Integer parentId;
    @TableField("name")
    @NotNull(message = "分类名称不为空")
    private String name;
    @TableField("sort_order")
    private int sortOrder;
    @TableField("status")
    private Integer status;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
