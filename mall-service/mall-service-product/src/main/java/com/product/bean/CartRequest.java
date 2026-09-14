package com.product.bean;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 购物车操作请求（加购 / 改数量 / 删除共用）。
 * 删除与改数量用同一个 body，quantity <= 0 表示删除（见 CartService.update）。
 */
@Data
public class CartRequest {

    @NotNull(message = "商品 id 不能为空")
    private Long productId;

    /** 数量：加购时为增量，改数量时为覆盖值；<=0 表示移除 */
    @Min(value = 0, message = "数量不能为负")
    private Integer quantity;
}
