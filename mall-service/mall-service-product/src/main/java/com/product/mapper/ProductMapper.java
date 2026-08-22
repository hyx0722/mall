package com.product.mapper;




import com.model.bean.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface ProductMapper {

    @Select("select name,subtitle,main_image,price from product  " +
            "where name=#{productName} " +
            "limit #{size} offset #{start}")
     List<Product> findProductByProductName(@Param("start") Integer start, @Param("size") Integer size, @Param("productName") String productName);

    @Select("select name,subtitle,main_image,price from product " +
            "where user_id=(select user_id from mall_service_user.user where username=#{username}) " +
            "limit #{size} offset #{start}")
    List<Product> findProductByUserName(@Param("start") Integer start, @Param("size") Integer size, @Param("username") String username);



    @Select("select name,subtitle,main_image,price from product " +
            "where user_id=#{userId} " +
            "limit #{size} offset #{start}")
    List<Product> findProductByUserId(@Param("start") Integer start, @Param("size") Integer size, @Param("userId") Integer userId);

}
