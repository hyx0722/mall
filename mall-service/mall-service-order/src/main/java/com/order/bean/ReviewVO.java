package com.order.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评价的展示对象：评价本体 + 补全出来的买家用户名。
 *
 * <p>补 {@code buyerName} 是因为评价表只存 {@code user_id}，而列表要显示「谁评的」。
 * 走跨库 join {@code mall_service_user.user}（本仓既有做法，见 {@code OrderMapper.findSellerOrders}
 * 的 {@code left join ... u.username as buyer_name}）。
 *
 * <p><b>商品名与图片直接取评价表里的快照字段</b>，不 join 商品库——
 * 这正是把 {@code product_name}/{@code product_image} 冗余进评价表的目的：
 * 评价的读取路径完全不依赖 product 服务。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {

    private Long id;

    private Long orderId;

    private Long productId;

    /** 下单时的商品名快照 */
    private String productName;

    private String productImage;

    /** 评价人用户名 */
    private String buyerName;

    private Integer rating;

    private String content;

    /** 商家回复；NULL 表示未回复 */
    private String replyContent;

    private LocalDateTime replyTime;

    private LocalDateTime createdTime;
}
