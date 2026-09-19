package com.order.bean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家回复评价的请求体。
 *
 * <p><b>没有 sellerId</b>：回复方只取登录态，且 UPDATE 语句自带
 * {@code and seller_id = 登录态} 条件——商家改不动别人商品的评价，不依赖任何前置查询。
 */
@Data
public class ReviewReplyRequest {

    @NotNull(message = "评价id不能为空")
    private Long reviewId;

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容不能超过 500 字")
    private String content;
}
