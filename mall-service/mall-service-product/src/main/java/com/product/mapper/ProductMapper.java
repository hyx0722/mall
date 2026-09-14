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
    @Select("<script>" +
            "select id,user_id,category_id,name,subtitle,main_image,price,original_price,status,created_time,updated_time " +
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
