package com.order.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品评价。一行 = 「某买家在某张订单里给某个商品的一条评价」。
 *
 * <p><b>写入的唯一入口是 {@code ProductReviewMapper.insertEligibleReview}</b>——
 * 它把「订单是我的、且已完成、且确实含这个商品」的资格判定做进了 INSERT…SELECT 本身，
 * 受影响行数 0 即不具备资格。**没有任何「直接插入评价」的路径**，
 * 所以本类只作为读取结果的载体和插入参数的载体。
 *
 * <p>字段的信任来源：{@code user_id} 取登录态，{@code seller_id} / {@code product_name} /
 * {@code product_image} 由插入语句从 {@code order_item} 与 {@code product} 表带出——
 * 客户端传不了，也就伪造不了。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("product_review")
public class ProductReview {

    /** 评分取值范围。与 DDL 的 CHECK (rating BETWEEN 1 AND 5) 对齐 */
    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    @TableField(value = "id")
    private Long id;

    @TableField(value = "order_id")
    private Long orderId;

    @TableField(value = "product_id")
    private Long productId;

    /** 下单时的商品名快照；商品改名后这里仍是旧名（刻意的，见 order.sql 的表注释） */
    @TableField(value = "product_name")
    private String productName;

    @TableField(value = "product_image")
    private String productImage;

    /** 评价人（买家） */
    @TableField(value = "user_id")
    private Long userId;

    /** 商品归属卖家，商家回复权限的唯一依据 */
    @TableField(value = "seller_id")
    private Long sellerId;

    /** 1-5 */
    @TableField(value = "rating")
    private Integer rating;

    @TableField(value = "content")
    private String content;

    /** 商家回复；NULL 表示未回复（回复的幂等条件就是它） */
    @TableField(value = "reply_content")
    private String replyContent;

    @TableField(value = "reply_time")
    private LocalDateTime replyTime;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;

    /**
     * 非持久化：评价人用户名，由 {@code selectReviewById} 跨库 join 带出。
     *
     * <p>存在的唯一理由是**发通知事件时免二次回查**：{@code insertEligibleReview} 只会回填
     * 自增主键，{@code seller_id}/{@code product_name} 这些是 SQL 的 SELECT 里算出来的，
     * Java 对象上根本没有值，所以插入后要按 id 读一次完整行才能把事件填齐。
     */
    @TableField(exist = false)
    private String buyerName;
}
