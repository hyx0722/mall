package com.model.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("product")
public class Product {
    // id 由数据库自增生成：同一实体同时作创建入参与展示出参，创建时不要求也不允许前端填主键
    @TableField("id")
    private Long id;
    @TableField("user_id")
    @NotNull(message = "上架者id不能为0")
    private Long userId;
    @TableField("category_id")
    @NotNull(message = "分类id不能为0")
    private Long categoryId;
    @TableField("name")
    @NotNull(message = "商品名字不能为0")
    private String name;
    @TableField(value = "subtitle")
    private String subtitle;
    @TableField(value = "main_image")
    @URL
    private String mainImage;
    @TableField(value = "detail")
    private String detail;
    @TableField(value = "price")
    @NotNull(message = "商品价格不能为0")
    private BigDecimal price;
    @TableField(value = "original_price")
    private BigDecimal originalPrice;
    @TableField(value = "status")
    private Integer status;
    @TableField(value = "created_time")
    private LocalDateTime createdTime;
    @TableField(value = "updated_time")
    private LocalDateTime updatedTime;
    // 非持久化：管理员商品列表联表带出的卖家用户名
    @TableField(exist = false)
    private String sellerName;

    /**
     * 非持久化：商品评价的平均分（由 ProductMapper 的相关子查询算出）。
     *
     * ⚠️ **无评价时是 NULL，不是 0**。前端必须据此整块不渲染星級——
     * 显示「0.0 分」会把「没人评过」误报成「差评」。
     */
    @TableField(exist = false)
    private BigDecimal avgRating;

    /** 非持久化：评价条数。与 {@link #avgRating} 配套，0 表示无评价 */
    @TableField(exist = false)
    private Integer reviewCount;
}
