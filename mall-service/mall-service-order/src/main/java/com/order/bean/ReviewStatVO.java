package com.order.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 某商品的评价汇总：平均分 + 总数 + 1-5 星分布（详情页顶部那块）。
 *
 * <p>分布用 {@link LinkedHashMap} 且**预填 1-5 全部档位**（值为 0），
 * 这样前端可以直接遍历渲染五根柱子，不必自己补零——
 * 只有非空档位的话，5 星商品的分布图会退化成一根柱子。
 */
@Data
public class ReviewStatVO {

    private Long productId;

    /** 平均分，**保留一位小数**。无评价时为 null（不是 0） */
    private BigDecimal avgRating;

    /** 评价总数 */
    private long total;

    /** 星级 -> 条数，键固定为 1..5（升序） */
    private Map<Integer, Long> distribution = new LinkedHashMap<>();

    public ReviewStatVO() {
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
    }
}
