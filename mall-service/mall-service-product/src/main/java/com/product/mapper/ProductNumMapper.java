package com.product.mapper;

import com.model.bean.Product;
import com.product.bean.UpdateProductRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductNumMapper {

    // 按唯一业务键(user_id,name)查询，用于上架前幂等判断
    @Select("select * from product where user_id=#{userId} and name=#{name}")
    Product findNumProductByUserIdAndName(@Param("userId") Integer userId, @Param("name") String name);

    // 依赖 uk_user_name(user_id,name) 唯一键在并发下兜底，重复上架抛 DuplicateKeyException
    @Insert("insert into product(user_id,category_id,name,subtitle,main_image,detail,price,original_price,status,created_time,updated_time) " +
            "values " +
            "(#{userId},#{categoryId},#{name},#{subtitle},#{mainImage},#{detail},#{price},#{originalPrice},#{status},now(),now())")
    void addNumProduct(Product product);

    // 归属校验 + 定位：取自己的某件商品
    @Select("select * from product where id=#{id} and user_id=#{userId}")
    Product findProductByIdAndUser(@Param("id") Integer id, @Param("userId") Integer userId);

    // 商家编辑：仅更新传入的非空字段，带 user_id 归属条件防越权
    @Update("<script>update product set updated_time=now()" +
            "<if test='name != null'> ,name=#{name}</if>" +
            "<if test='categoryId != null'> ,category_id=#{categoryId}</if>" +
            "<if test='subtitle != null'> ,subtitle=#{subtitle}</if>" +
            "<if test='mainImage != null'> ,main_image=#{mainImage}</if>" +
            "<if test='detail != null'> ,detail=#{detail}</if>" +
            "<if test='price != null'> ,price=#{price}</if>" +
            "<if test='originalPrice != null'> ,original_price=#{originalPrice}</if>" +
            "<if test='status != null'> ,status=#{status}</if>" +
            " where id=#{id} and user_id=#{userId}" +
            "</script>")
    int updateProduct(@Param("r") UpdateProductRequest r, @Param("userId") Integer userId);

    // 上/下架（带归属）
    @Update("update product set status=#{status}, updated_time=now() where id=#{id} and user_id=#{userId}")
    int updateProductStatus(@Param("id") Integer id, @Param("userId") Integer userId, @Param("status") Integer status);
}
