package com.order.mapper;

import com.order.bean.BuyerOrderItemVO;
import com.order.bean.ProductReview;
import com.order.bean.ReviewVO;
import com.order.bean.ReviewableOrderVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商品评价的读写。
 *
 * <p><b>刻意不继承 {@code BaseMapper}</b>：本表不需要 MyBatis-Plus 的 CRUD，
 * 而一旦继承、再声明与基类擦除后签名相同的方法会报「名称冲突」（{@code CouponMapper} 有先例注释）。
 * 同理**不要为此加 {@code @MapperScan}**——那会让 MyBatis-Plus 的自动扫描整体退避，
 * 本服务原有 mapper 全部停止注册，只在启动期炸。
 *
 * <p>本 mapper 里的 SQL 用了大量**跨库 join**（{@code mall_service_product.product}、
 * {@code mall_service_user.user}），这是本仓的既有做法——共用一个 MySQL 实例，
 * {@code OrderMapper.countMyItemLines} / {@code findSellerOrders} 早就这么写了。
 *
 * <p>⚠️ <b>越权红线</b>：凡是带 {@code orderId} 的查询都必须带 {@code o.user_id = #{userId}}
 * 或 {@code seller_id = #{sellerId}}。漏掉 SQL 照样跑、编译也过，结果是「任何登录用户
 * 能读/改任何人的数据」——本文件里最容易被改坏的就是 {@link #reply} 与
 * {@link #selectBuyerOrderItems}。
 */
@Mapper
public interface ProductReviewMapper {

    /**
     * 写入一条评价——<b>资格判定就在这条语句里</b>。
     *
     * <p>「订单是我的 + 订单已完成 + 订单确实含这个商品」三个条件全在 WHERE 上，
     * <b>受影响行数 = 0 即不具备资格</b>，没有任何 check-then-insert 窗口。
     * {@code seller_id} 取自商品表、{@code product_name}/{@code product_image} 取自明细快照，
     * 客户端无从伪造。
     *
     * <p>⚠️ <b>{@code and oi.id = (select min(...))} 那个条件不能删。</b>
     * {@code order_item} 上没有 {@code UNIQUE(order_id, product_id)}，而
     * {@code OrderServiceImpl.createOrder} 是逐条插入、不去重 productId 的——
     * 客户端传 {@code [{productId:7,qty:1},{productId:7,qty:2}]} 就能造出同商品的两行明细。
     * 没有这个条件时 join 出 2 行、试图插入 2 行相同 {@code (order_id, product_id)}，
     * 撞 {@code uk_order_product} 触发 <b>InnoDB 语句级回滚 → 0 行 + 异常</b>，
     * 结果是**这个买家永远评不了这个商品**，且报的是「系统繁忙」。
     * 用 {@code min()} 钉死一行即消除。
     *
     * <p>⚠️ 调用方**必须判断返回值**：忽略它既会让不合格的写入看起来像成功，
     * 也会为一个并不存在的评价发出通知事件。
     *
     * @return 受影响行数，1 = 成功，0 = 不具备资格
     */
    @Insert("insert into product_review(order_id, product_id, product_name, product_image,"
            + " user_id, seller_id, rating, content, created_time) "
            + "select o.id, oi.product_id, oi.product_name, oi.product_image,"
            + " o.user_id, p.user_id, #{rating}, #{content}, now() "
            + "from orders o "
            + "join order_item oi on oi.order_id = o.id and oi.product_id = #{productId} "
            + "join mall_service_product.product p on p.id = oi.product_id "
            + "where o.id = #{orderId} and o.user_id = #{userId} and o.order_status = 3 "
            + "and oi.id = (select min(oi2.id) from order_item oi2 "
            + "             where oi2.order_id = o.id and oi2.product_id = #{productId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEligibleReview(ProductReview review);

    /**
     * 按 id 读**完整**评价行（含跨库 join 出的 {@code buyerName}），供写入路径填事件体用。
     *
     * <p>为什么不复用 {@link #selectDetailById}：那个返回 {@code ReviewVO}，
     * 是给公开接口用的展示对象，不含 {@code seller_id}/{@code user_id} 这些内部归属字段
     * （公开接口没必要暴露用户主键）。事件体需要它们。
     *
     * <p>⚠️ 必须在插入**之后、同一事务内**调用才读得到刚写的行。
     */
    @Select("select r.*, u.username as buyer_name from product_review r "
            + "left join mall_service_user.user u on u.id = r.user_id "
            + "where r.id = #{id}")
    ProductReview selectReviewById(@Param("id") Long id);

    /**
     * 按唯一键取 id。是 {@code useGeneratedKeys} 在 {@code INSERT…SELECT} 上未回填时的退路。
     *
     * <p>（{@code @Options(useGeneratedKeys=true)} 配 INSERT…SELECT 不是常见组合，
     * 行为需要实测。即便它生效，留着这个方法也只是多一个几乎不会走到的分支。）
     */
    @Select("select id from product_review where order_id = #{orderId} and product_id = #{productId}")
    Long selectIdByOrderAndProduct(@Param("orderId") Long orderId,
                                   @Param("productId") Long productId);

    // ---------- 公开读取（商品详情页） ----------

    /**
     * 某商品的评价分页。
     * 买家用户名走跨库 join；商品名/图片直接取评价表的快照字段（不 join 商品库）。
     * {@code order by r.id desc} 的 id 是稳定分页的 tiebreaker——只按 created_time 排，
     * 同一秒的多条评价顺序不定，翻页会重复或漏。
     */
    @Select("select r.id, r.order_id, r.product_id, r.product_name, r.product_image,"
            + " r.rating, r.content, r.reply_content, r.reply_time, r.created_time,"
            + " u.username as buyer_name "
            + "from product_review r "
            + "left join mall_service_user.user u on u.id = r.user_id "
            + "where r.product_id = #{productId} "
            + "order by r.id desc limit #{size} offset #{offset}")
    List<ReviewVO> selectPageByProduct(@Param("productId") Long productId,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    @Select("select count(*) from product_review where product_id = #{productId}")
    long countByProduct(@Param("productId") Long productId);

    /**
     * 星级分布：只取非空档位，调用方在 Java 里补零并算总数/均值。
     *
     * <p>不在 SQL 里用 {@code avg()} 另算一遍均值——两处口径（一次来自分布、一次来自 avg）
     * 早晚会因某人改了过滤条件而漂移，且这种漂移不会有任何报错。
     */
    @Select("select r.rating as rating, count(*) as cnt from product_review r "
            + "where r.product_id = #{productId} group by r.rating")
    List<java.util.Map<String, Object>> selectRatingBuckets(@Param("productId") Long productId);

    /** 单条评价（公开）。通知深链靠它从 reviewId 反查 productId */
    @Select("select r.id, r.order_id, r.product_id, r.product_name, r.product_image,"
            + " r.rating, r.content, r.reply_content, r.reply_time, r.created_time,"
            + " u.username as buyer_name "
            + "from product_review r "
            + "left join mall_service_user.user u on u.id = r.user_id "
            + "where r.id = #{id}")
    ReviewVO selectDetailById(@Param("id") Long id);

    // ---------- 买家：可评价的订单 ----------

    /**
     * 我买过这个商品、订单已完成、且**该订单还没评过这个商品**的订单列表。
     *
     * <p>⚠️ {@code group by o.id} 不能少：重复明细行（见 {@link #insertEligibleReview} 的说明）
     * 会让同一张订单在结果里出现两次，弹框就会把同一订单列两遍。
     *
     * <p>「未评价」用 {@code not exists} 而不是 left join + is null——
     * 前者语义直白且不会因 join 出多行而重复。
     */
    @Select("select o.id as order_id, o.order_no, o.complete_time,"
            + " min(oi.product_name) as product_name, min(oi.product_image) as product_image "
            + "from orders o "
            + "join order_item oi on oi.order_id = o.id and oi.product_id = #{productId} "
            + "where o.user_id = #{userId} and o.order_status = 3 "
            + "and not exists (select 1 from product_review r "
            + "                where r.order_id = o.id and r.product_id = #{productId}) "
            + "group by o.id, o.order_no, o.complete_time "
            + "order by o.id desc")
    List<ReviewableOrderVO> selectReviewableOrders(@Param("productId") Long productId,
                                                   @Param("userId") Long userId);

    // ---------- 买家：订单明细 + 是否已评价 ----------

    /**
     * 买家视角的订单明细，附带每行的评价状态。
     *
     * <p>⚠️ {@code and o.user_id = #{userId}} 是**唯一的越权防线**：
     * 少了它，任何登录用户传别人的 orderId 就能读到别人的订单明细，200 OK、无日志。
     * 这里不复用 {@code OrderItemMapper.selectByOrderId}——它只 select 三列，
     * {@code productName}/{@code productPrice} 全返回 null，界面上会静默空白。
     *
     * <p>{@code can_review} 由「订单已完成 且 还没评过」推出；
     * {@code review_id} 让前端区分「去评价」和「查看评价」。
     * 同一商品的多行明细会各返回一行（都指向同一条评价），这是可接受的——
     * 界面按商品聚合展示即可。
     */
    @Select("select oi.product_id, oi.product_name, oi.product_image, oi.product_price,"
            + " oi.quantity, oi.total_price, oi.created_time,"
            + " (o.order_status = 3 and r.id is null) as can_review,"
            + " r.id as review_id "
            + "from order_item oi "
            + "join orders o on o.id = oi.order_id "
            + "left join product_review r on r.order_id = o.id and r.product_id = oi.product_id "
            + "where oi.order_id = #{orderId} and o.user_id = #{userId} "
            + "order by oi.id")
    List<BuyerOrderItemVO> selectBuyerOrderItems(@Param("orderId") Long orderId,
                                                 @Param("userId") Long userId);

    // ---------- 商家 ----------

    /**
     * 商家查看自己商品的评价。
     *
     * <p>⚠️ {@code onlyUnreplied} 这个条件必须在**本方法与 {@link #countSellerReviews}
     * 两处完全一致**。只改一处的后果是 total 与实际条数不符、最后一页变短，
     * 而且没有任何报错——分页错乱是最难被发现的一类 bug。
     */
    @Select("<script>select r.id, r.order_id, r.product_id, r.product_name, r.product_image,"
            + " r.rating, r.content, r.reply_content, r.reply_time, r.created_time,"
            + " u.username as buyer_name "
            + "from product_review r "
            + "left join mall_service_user.user u on u.id = r.user_id "
            + "where r.seller_id = #{sellerId} "
            + "<if test='onlyUnreplied'> and r.reply_content is null</if> "
            + "order by r.id desc limit #{size} offset #{offset}</script>")
    List<ReviewVO> selectSellerReviews(@Param("sellerId") Long sellerId,
                                       @Param("onlyUnreplied") boolean onlyUnreplied,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /** 与 {@link #selectSellerReviews} 同条件的总数，供 PageBean.total */
    @Select("<script>select count(*) from product_review r where r.seller_id = #{sellerId} "
            + "<if test='onlyUnreplied'> and r.reply_content is null</if></script>")
    long countSellerReviews(@Param("sellerId") Long sellerId,
                            @Param("onlyUnreplied") boolean onlyUnreplied);

    /**
     * 商家回复。
     *
     * <p>两个条件都**不能漏**，且两种漏法都是静默的：
     * <ul>
     *   <li>{@code and seller_id = #{sellerId}} —— 漏了就是**静默 IDOR**：
     *       任何登录用户能假冒任何商家回复，UPDATE 成功、无报错、返回 200；</li>
     *   <li>{@code and reply_content is null} —— 漏了则「一条回复」变成可任意覆盖，
     *       回复路径也失去幂等保护（重复投递会改写已有回复）。</li>
     * </ul>
     *
     * @return 受影响行数，0 即「评价不存在 / 不属于你 / 已回复过」
     */
    @Update("update product_review set reply_content = #{content}, reply_time = now() "
            + "where id = #{reviewId} and seller_id = #{sellerId} and reply_content is null")
    int reply(@Param("reviewId") Long reviewId,
              @Param("sellerId") Long sellerId,
              @Param("content") String content);
}
