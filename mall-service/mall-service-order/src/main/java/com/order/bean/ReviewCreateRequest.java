package com.order.bean;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 买家写评价的请求体。
 *
 * <p><b>没有 userId</b>：评价人只取登录态。而 {@code orderId} / {@code productId} 虽然由客户端传，
 * 但**传什么都不影响归属**——{@code insertEligibleReview} 会用
 * 「o.user_id = 登录态」把它们约束成「只能评自己的、已完成的、确实买了的订单」。
 * 伪造别家的订单号只会得到 0 行。
 *
 * <p>校验注解一个都不能少，且失败方式各不相同：
 * <ul>
 *   <li>{@code @Size(max = 500)} 漏了 → 严格 SQL 模式报「系统繁忙」，非严格模式**静默截断**；</li>
 *   <li>{@code @NotBlank} 漏了 → 纯空白的评价能入库；</li>
 *   <li>{@code @Min/@Max} 漏了 → 越界评分靠 DDL 的 CHECK 兜底，报的还是「系统繁忙」而不是友好提示。</li>
 * </ul>
 */
@Data
public class ReviewCreateRequest {

    @NotNull(message = "订单id不能为空")
    private Long orderId;

    @NotNull(message = "商品id不能为空")
    private Long productId;

    @NotNull(message = "评分不能为空")
    @Min(value = ProductReview.MIN_RATING, message = "评分最低 1 星")
    @Max(value = ProductReview.MAX_RATING, message = "评分最高 5 星")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;
}
