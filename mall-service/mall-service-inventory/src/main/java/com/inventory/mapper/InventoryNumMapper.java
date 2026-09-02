package com.inventory.mapper;

import com.model.bean.Inventory;
import com.model.bean.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryNumMapper {

    @Select("select * from inventory where product_id=#{id} and user_id=#{userId}")
    Inventory findNumInventory(Product product);

    @Insert("insert into " +
            "inventory(product_id,user_id,total_stock,locked_stock,available_stock,sales_count,version,created_time,updated_time) " +
            "values " +
            "(#{id},#{userId},0,0,0,0,1,now(),now())")
    void addNumInventory(Product product);
}
