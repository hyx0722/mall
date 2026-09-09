package com.product.mapper;


import com.model.bean.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("select id,name,subtitle,main_image,price,status from product " +
            "where user_id=#{userId} " +
            "limit #{size} offset #{offset}")
    List<Product> findProductByUserId(@Param("offset") Integer offset, @Param("size") Integer size, @Param("userId") Long userId);

    // 供下单服务同步拉取商品快照（全字段）
    @Select("select * from product where id=#{id}")
    Product findProductById(@Param("id") Long id);

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
