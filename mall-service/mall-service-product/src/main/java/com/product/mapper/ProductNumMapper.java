package com.product.mapper;

import com.model.bean.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductNumMapper {
    @Insert("insert into product(user_id,category_id,name,subtitle,main_image,detail,price,original_price,status,created_time,updated_time) " +
            "values " +
            "(#{userId},#{categoryId},#{name},#{subtitle},#{mainImage},#{detail},#{price},#{originalPrice},#{status},#{createdTime},#{updatedTime})")
    void addProduct(Product product);
}
