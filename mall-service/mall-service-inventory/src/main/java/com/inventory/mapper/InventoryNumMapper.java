package com.inventory.mapper;

import com.model.bean.Inventory;
import com.model.bean.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface InventoryNumMapper {

    @Select("select * from inventory where product_id=#{id} and user_id=#{userId}")
    Inventory findNumInventory(Product product);

    // 校验/定位商家自己的某条库存（补货前）
    @Select("select * from inventory where product_id=#{productId} and user_id=#{userId}")
    Inventory findByProductIdAndUser(@Param("productId") Long productId, @Param("userId") Long userId);

    @Insert("insert into " +
            "inventory(product_id,user_id,total_stock,locked_stock,available_stock,sales_count,created_time,updated_time) " +
            "values " +
            "(#{id},#{userId},0,0,0,0,now(),now())")
    void addNumInventory(Product product);

    // 商家补货：仅增加总库存与可用库存，锁定库存不动；带归属条件（不超卖由条件 UPDATE 保证，去掉无意义的版本自增）
    @Update("update inventory set total_stock=total_stock+#{qty}, available_stock=available_stock+#{qty}, " +
            "updated_time=now() " +
            "where product_id=#{productId} and user_id=#{userId}")
    int addStock(@Param("productId") Long productId, @Param("userId") Long userId, @Param("qty") Integer qty);

    // 管理员：查询库存（可按商品 id 过滤）；跨库带商品名与卖家用户名
    @Select("<script>" +
            "select i.id,i.product_id,i.user_id,i.total_stock,i.locked_stock,i.available_stock,i.sales_count,i.version,i.created_time,i.updated_time," +
            " p.name as product_name, u.username as seller_name " +
            "from inventory i " +
            "left join mall_service_product.product p on p.id=i.product_id " +
            "left join mall_service_user.user u on u.id=i.user_id " +
            "where 1=1" +
            "<if test='productId != null'> and i.product_id=#{productId}</if>" +
            " order by i.id desc" +
            "</script>")
    List<Inventory> findAllInventory(@Param("productId") Long productId);
}
