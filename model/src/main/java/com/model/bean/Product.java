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
    @TableField("id")
    @NotNull(message = "商品id不能为0")
    private Integer id;
    @TableField("user_id")
    @NotNull(message = "上架者id不能为0")
    private Integer userId;
    @TableField("category_id")
    @NotNull(message = "分类id不能为0")
    private Integer categoryId;
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
}
