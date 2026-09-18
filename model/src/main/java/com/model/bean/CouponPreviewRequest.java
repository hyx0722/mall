package com.model.bean;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用券试算请求（order 服务下单时同步调用 user 服务）。
 *
 * 放在 model 而非 user 服务：它是 order 与 user 之间的**跨服务契约**，
 * 与 {@link Product} 同样被两端共用（依赖方向 业务服务 -> mall-common -> model）。
 *
 * 为什么由调用方把明细传过来、而不是 user 服务回查商品库：
 * 判定「指定商品 / 分类限定」需要每个商品的 categoryId，而 order 在下单快照循环里
 * 已经从 product 服务拿到了 {@link Product}（含 categoryId），传过来省一次跨服务往返。
 * 传的是**快照那一刻**的价格，与订单落库的口径天然一致。
 */
@Data
public class CouponPreviewRequest {

    /**
     * 要试算的券。**刻意不加 @NotNull**：本 DTO 被两个契约不同的接口共用——
     * <ul>
     *   <li>{@code POST /coupon/preview}（order 服务调用）必带此字段；</li>
     *   <li>{@code POST /coupon/usable}（结算页拉可用券列表）**只传 lines**，
     *       它是对用户所有未使用券批量评估，天然没有「某一 张券」的概念。</li>
     * </ul>
     * 加了 @NotNull 会让后者一律 400，而前端为了「拉不到券不影响结算」把异常吞掉，
     * 表现为**券卡片永远不出现**、且只有一条被忽略的报错——极难从现象反推。
     * 缺 id 的场景由 {@code CouponServiceImpl.preview} 显式判空并给出文案。
     */
    private Long userCouponId;

    // @Valid 不能少：缺了它，Line 内部的 @NotNull 全都不会被触发（嵌套校验要显式开启），
    // 于是「明细缺 productId / lineTotal」能一路走到 service，报成看不懂的 NPE 或算错钱
    @NotEmpty(message = "缺少订单明细")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull
        private Long productId;
        /** 商品所属分类；未分类时为 null，此时只能命中「指定商品」类型的范围 */
        private Long categoryId;
        /** 该行小计 = 单价 × 数量（由调用方按其快照算出，避免两边口径漂移） */
        @NotNull
        private BigDecimal lineTotal;
    }
}
