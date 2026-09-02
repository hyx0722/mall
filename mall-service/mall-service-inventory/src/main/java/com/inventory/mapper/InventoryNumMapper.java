package com.inventory.mapper;

import com.model.bean.Inventory;
import com.model.bean.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryNumMapper {

    @Select("select * from inventory where product_id=#{id} and user_id=#{userId}")
    Inventory findNumInventory(Product product);

    // 校验/定位商家自己的某条库存（补货前）
    @Select("select * from inventory where product_id=#{productId} and user_id=#{userId}")
    Inventory findByProductIdAndUser(@Param("productId") Integer productId, @Param("userId") Integer userId);

    @Insert("insert into " +
            "inventory(product_id,user_id,total_stock,locked_stock,available_stock,sales_count,version,created_time,updated_time) " +
            "values " +
            "(#{id},#{userId},0,0,0,0,1,now(),now())")
    void addNumInventory(Product product);

    // 商家补货：仅增加总库存与可用库存，锁定库存不动；带归属条件 + 版本自增（乐观锁）
    @Update("update inventory set total_stock=total_stock+#{qty}, available_stock=available_stock+#{qty}, " +
            "version=version+1, updated_time=now() " +
            "where product_id=#{productId} and user_id=#{userId}")
    int addStock(@Param("productId") Integer productId, @Param("userId") Integer userId, @Param("qty") Integer qty);
}
