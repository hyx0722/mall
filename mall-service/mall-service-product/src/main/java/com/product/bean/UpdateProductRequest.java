package com.product.bean;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * 商家编辑商品请求：除 id 外全部可选（只更新传入的非空字段）。
 * Hibernate Validator 对 @URL/@DecimalMin/@Size 在值为 null 时视为合法，故可直接用于部分更新。
 */
@Data
public class UpdateProductRequest {

    @NotNull(message = "商品 id 不能为空")
    private Long id;

    @Size(max = 200, message = "商品名称最长 200 字")
    private String name;

    private Long categoryId;

    @Size(max = 200, message = "副标题最长 200 字")
    private String subtitle;

    @URL(message = "主图需为合法 URL")
    private String mainImage;

    @Size(max = 10000, message = "商品详情过长")
    private String detail;

    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    private BigDecimal originalPrice;

    /** 1-上架 0-下架 */
    private Integer status;
}
