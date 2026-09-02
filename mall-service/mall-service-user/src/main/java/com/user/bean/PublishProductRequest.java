package com.user.bean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * 商家发布商品入参。
 * 不含 id / userId：商品主键由 DB 自增回填，上架者 id 由系统从登录态自动填充。
 */
@Data
public class PublishProductRequest {

    @NotNull(message = "分类id不能为0")
    private Long categoryId;

    @NotBlank(message = "商品名字不能为0")
    private String name;

    private String subtitle;

    @URL
    private String mainImage;

    private String detail;

    @NotNull(message = "商品价格不能为0")
    private BigDecimal price;

    private BigDecimal originalPrice;
}
