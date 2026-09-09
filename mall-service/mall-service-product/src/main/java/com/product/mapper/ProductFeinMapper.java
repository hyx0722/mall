package com.product.mapper;

import com.model.bean.Product;
import com.product.bean.ProductUpdateRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductFeinMapper {

    // 按唯一业务键(user_id,name)查询，用于上架前幂等判断
    @Select("select * from product where user_id=#{userId} and name=#{name}")
    Product findNumProductByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    // 依赖 uk_user_name(user_id,name) 唯一键在并发下兜底，重复上架抛 DuplicateKeyException
    // useGeneratedKeys 回填自增主键，供“建商品->初始化库存”链路把 id 传给库存服务
    @Insert("insert into product(user_id,category_id,name,subtitle,main_image,detail,price,original_price,status,created_time,updated_time) " +
            "values " +
            "(#{userId},#{categoryId},#{name},#{subtitle},#{mainImage},#{detail},#{price},#{originalPrice},#{status},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int addNumProduct(Product product);

    // 归属校验 + 定位：取自己的某件商品
    @Select("select * from product where id=#{id} and user_id=#{userId}")
    Product findProductByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    // 商家编辑：仅更新传入的非空字段，带 user_id 归属条件防越权
    @Update("<script>update product set updated_time=now()" +
            "<if test='r.name != null'> ,name=#{r.name}</if>" +
            "<if test='r.categoryId != null'> ,category_id=#{r.categoryId}</if>" +
            "<if test='r.subtitle != null'> ,subtitle=#{r.subtitle}</if>" +
            "<if test='r.mainImage != null'> ,main_image=#{r.mainImage}</if>" +
            "<if test='r.detail != null'> ,detail=#{r.detail}</if>" +
            "<if test='r.price != null'> ,price=#{r.price}</if>" +
            "<if test='r.originalPrice != null'> ,original_price=#{r.originalPrice}</if>" +
            "<if test='r.status != null'> ,status=#{r.status}</if>" +
            " where id=#{r.id} and user_id=#{userId}" +
            "</script>")
    int updateProduct(@Param("r") ProductUpdateRequest r, @Param("userId") Long userId);

    // 上/下架（带归属）
    @Update("update product set status=#{status}, updated_time=now() where id=#{id} and user_id=#{userId}")
    int updateProductStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("status") Integer status);


}
