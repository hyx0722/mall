package com.product.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品分类（表 category）。纯展示/传输对象：MyBatis 按列名自动驼峰映射，
 * children 为树形接口临时填充，不落表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Category {
    private Long id;
    /** 父分类 id，0 表示顶级分类 */
    private Long parentId = 0L;
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 字")
    private String name;
    private Integer sortOrder = 0;
    /** 1-启用，0-禁用 */
    private Integer status = 1;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    /** 子分类（非表字段，仅在 tree 接口填充） */
    private List<Category> children;
}
