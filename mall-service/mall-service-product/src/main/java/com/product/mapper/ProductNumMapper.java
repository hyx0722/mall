package com.product.mapper;

import com.model.bean.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductNumMapper {
    @Insert("insert into product(user_id,category_id,name,subtitle,main_image,detail,price,original_price,status,created_time,updated_time) " +
            "values " +
            "(#{userId},#{categoryId},#{name},#{subtitle},#{mainImage},#{detail},#{price},#{originalPrice},#{status},#{createdTime},#{updatedTime})")
    void addNumProduct(Product product);

    @Select("select * from product where user_id=#{userId} and id=#{id}")
    Product findNumProduct(Product product);
}
