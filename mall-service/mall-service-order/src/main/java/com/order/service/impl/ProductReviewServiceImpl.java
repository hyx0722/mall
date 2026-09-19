package com.order.service.impl;

import com.mall.common.outbox.OutboxService;
import com.model.bean.PageBean;
import com.model.event.ReviewCreatedEvent;
import com.model.event.ReviewRepliedEvent;
import com.model.exception.BusinessException;
import com.order.bean.BuyerOrderItemVO;
import com.order.bean.ProductReview;
import com.order.bean.ReviewCreateRequest;
import com.order.bean.ReviewReplyRequest;
import com.order.bean.ReviewStatVO;
import com.order.bean.ReviewVO;
import com.order.bean.ReviewableOrderVO;
import com.order.config.OrderRabbitConfig;
import com.order.mapper.ProductReviewMapper;
import com.order.service.ProductReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ProductReviewServiceImpl implements ProductReviewService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    /** 平均分保留一位小数。两位太细（4.33 与 4.35 对买家没区别），整数太粗 */
    private static final int AVG_SCALE = 1;

    @Autowired
    ProductReviewMapper reviewMapper;
    @Autowired
    OutboxService outboxService;

    // ---------- 买家：写 ----------

    @Override
    @Transactional
    public void create(Long userId, ReviewCreateRequest request) {
        ProductReview review = new ProductReview();
        review.setOrderId(request.getOrderId());
        review.setProductId(request.getProductId());
        // ⚠️ 这一行是资格判定的**关键输入**：SQL 里 `where o.user_id = #{userId}` 靠它，
        // 漏掉的话绑定成 NULL、条件永不成立、**每一条评价都被拒**——
        // 表现是「所有请求都失败」而不是「越权成功」，属于失败但安全的方向。
        // 注意它不是来自 request（请求体里根本没有这个字段），而是登录态。
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setContent(request.getContent().trim());

        int affected;
        try {
            // 资格判定就在这条 INSERT…SELECT 里：订单是我的 + 已完成 + 确实含这个商品。
            // 不先 SELECT 一次是有意的——那会引入检查与写入之间的窗口，
            // 而「并发退款」把订单推到 5 的场景下，那个窗口是真实存在的。
            affected = reviewMapper.insertEligibleReview(review);
        } catch (DuplicateKeyException e) {
            // 撞 uk_order_product：这一单的这个商品已经评过了。
            // 必须转成业务异常——不捕获就会冒泡到 GlobalExceptionHandler 变成「系统繁忙」，
            // 数据没错但用户完全看不懂。
            throw new BusinessException("你已经评价过这个商品了");
        }

        if (affected == 0) {
            // 四种原因共用这一个分支：不是我的订单 / 订单不是已完成 / 订单里没这个商品 /
            // 商品已被物理删除。**不要**为了让文案更精确而在前面加校验查询——
            // 那既要把判定逻辑写两遍，又会把窗口放回来。
            throw new BusinessException("评价失败：订单不存在、未完成，或该订单中没有这个商品");
        }

        enqueueCreated(review);
    }

    /**
     * 入箱 {@code review.created}，与评价行同一事务。
     *
     * <p>⚠️ 这里必须**按 id 重读一次**完整行：{@code insertEligibleReview} 是 INSERT…SELECT，
     * 只有自增主键会被回填到 Java 对象上；{@code seller_id} / {@code product_name} /
     * 买家用户名都是在 SQL 的 SELECT 里算出来的，Java 对象上始终是 null。
     * 直接拿它拼事件，卖家 id 会是 null，通知根本发不出去（`record` 会静默跳过）。
     */
    private void enqueueCreated(ProductReview inserted) {
        Long reviewId = inserted.getId();
        if (reviewId == null) {
            // @Options(useGeneratedKeys=true) 配 INSERT…SELECT 不是常见组合，
            // 万一不回填就走唯一键回查（同一事务内，刚写的行可见）
            reviewId = reviewMapper.selectIdByOrderAndProduct(inserted.getOrderId(), inserted.getProductId());
            log.warn("[review] useGeneratedKeys 未回填，已按唯一键回查 reviewId={}", reviewId);
        }
        if (reviewId == null) {
            // 评价已经写成功了，只是通知发不出去。不抛异常——那会把一次成功的评价变成失败。
            log.error("[review] 无法确定新评价 id，跳过通知事件。orderId={} productId={}",
                    inserted.getOrderId(), inserted.getProductId());
            return;
        }

        ProductReview saved = reviewMapper.selectReviewById(reviewId);
        if (saved == null) {
            log.error("[review] 评价已写入但读不到本体，跳过通知事件。reviewId={}", reviewId);
            return;
        }

        ReviewCreatedEvent event = new ReviewCreatedEvent();
        event.setReviewId(saved.getId());
        event.setOrderId(saved.getOrderId());
        event.setProductId(saved.getProductId());
        event.setProductName(saved.getProductName());
        event.setSellerId(saved.getSellerId());
        event.setBuyerName(saved.getBuyerName());
        event.setRating(saved.getRating());
        event.setContent(saved.getContent());
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_REVIEW_CREATED, null, event);
    }

    // ---------- 公开读取 ----------

    @Override
    public PageBean<ReviewVO> pageByProduct(Long productId, Integer page, Integer size) {
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        int p = (page == null || page < 1) ? 1 : page;
        int s = normSize(size);
        long total = reviewMapper.countByProduct(productId);
        if (total == 0) {
            return new PageBean<>(0L, List.of());
        }
        return new PageBean<>(total, reviewMapper.selectPageByProduct(productId, (p - 1) * s, s));
    }

    @Override
    public ReviewStatVO stat(Long productId) {
        if (productId == null) {
            throw new BusinessException("缺少商品 id");
        }
        ReviewStatVO vo = new ReviewStatVO();
        vo.setProductId(productId);

        // 分布由 SQL 出（group by rating），总数与均值在 Java 里按分布算。
        // 不在 SQL 里另算一遍 avg()：两处口径早晚会因某人改了一处过滤条件而漂移，
        // 而且不会有任何报错——只是平均分与柱子对不上。
        long total = 0;
        long sum = 0;
        for (Map<String, Object> row : reviewMapper.selectRatingBuckets(productId)) {
            Object ratingObj = row.get("rating");
            Object cntObj = row.get("cnt");
            if (ratingObj == null || cntObj == null) {
                continue;
            }
            int rating = ((Number) ratingObj).intValue();
            long cnt = ((Number) cntObj).longValue();
            if (vo.getDistribution().containsKey(rating)) {
                vo.getDistribution().put(rating, cnt);
            }
            total += cnt;
            sum += (long) rating * cnt;
        }
        vo.setTotal(total);
        if (total > 0) {
            vo.setAvgRating(BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(total), AVG_SCALE, RoundingMode.HALF_UP));
        }
        // total == 0 时 avgRating 保持 null —— 前端据此整块不渲染，
        // 而不是显示「0.0 分」（0 分是差评，null 是没人评过）
        return vo;
    }

    @Override
    public ReviewVO detail(Long id) {
        if (id == null) {
            throw new BusinessException("缺少评价 id");
        }
        ReviewVO vo = reviewMapper.selectDetailById(id);
        if (vo == null) {
            throw new BusinessException("评价不存在");
        }
        return vo;
    }

    // ---------- 买家：可评价的订单 / 订单明细 ----------

    @Override
    public List<ReviewableOrderVO> reviewableOrders(Long userId, Long productId) {
        if (productId == null) {
            return List.of();
        }
        return reviewMapper.selectReviewableOrders(productId, userId);
    }

    @Override
    public List<BuyerOrderItemVO> orderItems(Long userId, Long orderId) {
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 归属校验在 SQL 里（o.user_id = #{userId}），不是我的订单一律返回空列表。
        // 不额外回查订单表判归属——那是把同一个条件写两遍，早晚有一处会漏。
        return reviewMapper.selectBuyerOrderItems(orderId, userId);
    }

    // ---------- 商家 ----------

    @Override
    public PageBean<ReviewVO> sellerReviews(Long sellerId, Boolean onlyUnreplied, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = normSize(size);
        boolean unrepliedOnly = Boolean.TRUE.equals(onlyUnreplied);
        long total = reviewMapper.countSellerReviews(sellerId, unrepliedOnly);
        if (total == 0) {
            return new PageBean<>(0L, List.of());
        }
        return new PageBean<>(total, reviewMapper.selectSellerReviews(sellerId, unrepliedOnly, (p - 1) * s, s));
    }

    @Override
    @Transactional
    public void reply(Long sellerId, ReviewReplyRequest request) {
        String content = request.getContent().trim();
        // 条件 UPDATE 一次挡住三件事：不存在 / 不是我的商品 / 已回复过。
        // seller_id 与 reply_content is null 两个条件缺一不可——
        // 漏 seller_id 就是静默越权（任何登录用户能假冒任何商家回复），
        // 详见 ProductReviewMapper.reply 的说明。
        if (reviewMapper.reply(request.getReviewId(), sellerId, content) == 0) {
            throw new BusinessException("回复失败：评价不存在、不属于你的商品，或你已经回复过了");
        }

        // UPDATE 只返回行数，而通知要发给买家、还带上商品信息，所以按 id 回读一次。
        // 这次读取发生在 seller_id 校验**通过之后**，不存在越权读取。
        ProductReview saved = reviewMapper.selectReviewById(request.getReviewId());
        if (saved == null) {
            log.warn("[review] 回复成功但读不到评价本体，跳过通知 reviewId={}", request.getReviewId());
            return;
        }
        ReviewRepliedEvent event = new ReviewRepliedEvent();
        event.setReviewId(saved.getId());
        event.setOrderId(saved.getOrderId());
        event.setProductId(saved.getProductId());
        event.setProductName(saved.getProductName());
        event.setSellerId(saved.getSellerId());
        event.setBuyerId(saved.getUserId());
        event.setReplyContent(content);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_REVIEW_REPLIED, null, event);
    }

    // ---------- 内部 ----------

    private static int normSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
