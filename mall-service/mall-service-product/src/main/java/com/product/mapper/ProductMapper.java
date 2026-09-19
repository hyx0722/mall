package com.product.mapper;


import com.model.bean.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;


@Mapper
public interface ProductMapper {

    // 按名称模糊查询在售商品（买家浏览入口）
    @Select("select id,name,subtitle,main_image,price from product " +
            "where status=1 and name like concat('%',#{productName},'%') " +
            "limit #{size} offset #{offset}")
    List<Product> findProductByProductName(@Param("offset") Integer offset, @Param("size") Integer size, @Param("productName") String productName);

    // 按卖家用户名查其发布的在售商品（保留既有跨库 mall_service_user 依赖）
    @Select("select id,name,subtitle,main_image,price from product " +
            "where user_id=(select id from mall_service_user.user where username=#{username}) and status=1 " +
            "limit #{size} offset #{offset}")
    List<Product> findProductByUserName(@Param("offset") Integer offset, @Param("size") Integer size, @Param("username") String username);

    // 查看自己发布的商品（商家后台，含已下架）
    // order by id desc 必须有：没有 ORDER BY 时 MySQL 不保证两次 OFFSET 查询的行序一致，
    // 翻页会出现商品重复或漏掉
    @Select("select id,name,subtitle,main_image,price,status from product " +
            "where user_id=#{userId} " +
            "order by id desc " +
            "limit #{size} offset #{offset}")
    List<Product> findProductByUserId(@Param("offset") Integer offset, @Param("size") Integer size, @Param("userId") Long userId);

    // 自己发布的商品总数（含已下架），供分页计算总页数、判断有无下一页
    @Select("select count(*) from product where user_id=#{userId}")
    long countProductByUserId(@Param("userId") Long userId);

    // 供下单服务同步拉取商品快照（全字段）
    @Select("select * from product where id=#{id}")
    Product findProductById(@Param("id") Long id);

    // 批量按 id 查商品（购物车读取时补全名称/价格/主图，避免逐条查库）
    @Select("<script>" +
            "select id,user_id,name,subtitle,main_image,price,status from product " +
            "where id in <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Product> findByIds(@Param("ids") Collection<Long> ids);

    // 浏览列表：关键词模糊 + 分类筛选 + 白名单排序 + 分页（仅 status=1 在售）
    //
    // 末尾两个**相关子查询**带出评价聚合（商品卡片要显示星级）。
    //
    // ⚠️ 刻意不用 `left join product_review … group by p.id`：
    //    ① GROUP BY 在 LIMIT 之前聚合，必须扫完所有匹配商品，废掉索引分页；
    //    ② 它只在 p.id 是主键时靠函数依赖合法（MySQL 8 的 ONLY_FULL_GROUP_BY），
    //       换成非键列分组就运行时炸——给下一个人埋雷；
    //    ③ 还得同步改 countProductList，两处条件漂移会让翻页错乱。
    //    子查询只对返回的这几行各走一次 idx_product_rating，且完全不碰原查询结构。
    //
    // ⚠️ 这是本模块唯一一处**跨库读 order 库**。本仓已有跨库读先例
    //    （下面 findProductByUserName 读 mall_service_user.user），共用一个 MySQL 实例。
    //    但要清楚 blast radius：/product/list 是**公开**接口，若 mall_service_order 库
    //    不存在（全新克隆、order 服务从没启动过），这里会让整个商品浏览 500，
    //    而不是「评分不显示」。真要收紧边界，砍掉这两行即可。
    //
    // 无评价时 avg_rating 是 NULL（不是 0），前端据此整块不渲染——
    // 显示「0.0 分」会把「没人评过」误报成「差评」。
    @Select("<script>" +
            "select id,user_id,category_id,name,subtitle,main_image,price,original_price,status,created_time,updated_time, " +
            "(select avg(r.rating) from mall_service_order.product_review r where r.product_id = product.id) as avg_rating, " +
            "(select count(*)      from mall_service_order.product_review r where r.product_id = product.id) as review_count " +
            "from product where status=1 " +
            "<if test='keyword != null and keyword != \"\"'> and name like concat('%',#{keyword},'%')</if>" +
            "<if test='categoryId != null'> and category_id=#{categoryId}</if>" +
            " order by " +
            "<choose>" +
            "<when test=\"sort == 'price_asc'\">price asc, id asc</when>" +
            "<when test=\"sort == 'price_desc'\">price desc, id asc</when>" +
            "<otherwise>created_time desc, id desc</otherwise>" +
            "</choose>" +
            " limit #{size} offset #{offset}" +
            "</script>")
    List<Product> findProductList(@Param("keyword") String keyword,
                                  @Param("categoryId") Long categoryId,
                                  @Param("sort") String sort,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    // 浏览列表总数（与 findProductList 同条件，供 PageBean.total）
    @Select("<script>" +
            "select count(*) from product where status=1 " +
            "<if test='keyword != null and keyword != \"\"'> and name like concat('%',#{keyword},'%')</if>" +
            "<if test='categoryId != null'> and category_id=#{categoryId}</if>" +
            "</script>")
    long countProductList(@Param("keyword") String keyword,
                          @Param("categoryId") Long categoryId);



}
