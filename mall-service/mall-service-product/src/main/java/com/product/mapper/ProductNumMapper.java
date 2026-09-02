package com.product.mapper;

import com.model.bean.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
