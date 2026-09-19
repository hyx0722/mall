package com.order.service;

import com.model.bean.PageBean;
import com.order.bean.BuyerOrderItemVO;
import com.order.bean.ReviewCreateRequest;
import com.order.bean.ReviewReplyRequest;
import com.order.bean.ReviewStatVO;
import com.order.bean.ReviewVO;
import com.order.bean.ReviewableOrderVO;

import java.util.List;

/**
 * 商品评价。
 *
 * <p>资格规则只有一条：**订单已完成（order_status=3）且该订单确实含这个商品**，
 * 且每个订单每个商品只能评一条（「买两次可评两次」）。
 * 判定本身不在这里做——它被写进了 {@code ProductReviewMapper.insertEligibleReview} 的
 * INSERT…SELECT 里，见 {@link #create} 的说明。
 *
 * <p>所有方法的 {@code userId}/{@code sellerId} 都取自登录态，**没有任何方法收它作参数**。
 */
public interface ProductReviewService {

    /**
     * 买家写评价。
     *
     * <p>「是不是买过」的判定在 SQL 里（受影响行数 0 = 不具备资格），
     * 所以这里既不需要先查一次订单、也不存在检查与写入之间的窗口。
     * 成功后与评价行**同一事务**投递 {@code review.created} 通知卖家。
     */
    void create(Long userId, ReviewCreateRequest request);

    /** 某商品的评价分页（公开） */
    PageBean<ReviewVO> pageByProduct(Long productId, Integer page, Integer size);

    /** 某商品的评价汇总（公开）。无评价时 {@code avgRating} 为 null、{@code total} 为 0 */
    ReviewStatVO stat(Long productId);

    /** 单条评价（公开）。通知深链用它从 reviewId 反查 productId */
    ReviewVO detail(Long id);

    /** 我买过该商品、订单已完成、且尚未评价的订单列表（写评价弹框的订单选择器） */
    List<ReviewableOrderVO> reviewableOrders(Long userId, Long productId);

    /** 买家视角的订单明细 + 每行能否评价（订单详情页） */
    List<BuyerOrderItemVO> orderItems(Long userId, Long orderId);

    /** 商家：我商品的评价分页。{@code onlyUnreplied} 只列未回复的 */
    PageBean<ReviewVO> sellerReviews(Long sellerId, Boolean onlyUnreplied, Integer page, Integer size);

    /** 商家回复。只有该商品的卖家能回复、且只能回复一次 */
    void reply(Long sellerId, ReviewReplyRequest request);
}
