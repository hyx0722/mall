package com.product.mapper;

import com.model.bean.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
@Mapper
public interface ProductAdminMapper {

    // ---------- 管理员：查看所有商品（含下架，跨库带卖家用户名） ----------

    @Select("<script>" +
            "select p.id,p.user_id,p.category_id,p.name,p.subtitle,p.main_image,p.detail,p.price,p.original_price,p.status,p.created_time,p.updated_time,u.username as seller_name " +
            "from product p left join mall_service_user.user u on u.id=p.user_id where 1=1" +
            "<if test='kw != null and kw != \"\"'> and p.name like concat('%',#{kw},'%')</if>" +
            " order by p.id desc limit #{size} offset #{offset}" +
            "</script>")
    List<Product> pageAllProducts(@Param("kw") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>" +
            "select count(*) from product p where 1=1" +
            "<if test='kw != null and kw != \"\"'> and p.name like concat('%',#{kw},'%')</if>" +
            "</script>")
    long countAllProducts(@Param("kw") String keyword);

    // 管理员对任意商品上/下架（不带归属）
    @Update("update product set status=#{status}, updated_time=now() where id=#{id}")
    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
}
