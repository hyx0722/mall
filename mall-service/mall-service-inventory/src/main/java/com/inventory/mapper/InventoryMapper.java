package com.inventory.mapper;

import com.model.bean.Inventory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper {

    @Insert("insert into inventory(product_id,user_id,total_stock,locked_stock,available_stock,updated_time)" +
            " values " +
            "(#{productId},#{userId},#{totalStock},#{lockedStock},#{availableStock},now())")
    void updateInventory(Inventory inventory);
}
