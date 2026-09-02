package com.inventory.mapper;

import com.inventory.bean.InventoryLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryLogMapper {

    @Insert("insert into inventory_log(product_id,order_id,change_type,change_quantity," +
            "before_total_stock,after_total_stock,before_locked_stock,after_locked_stock,remark,created_time) " +
            "values(#{productId},#{orderId},#{changeType},#{changeQuantity}," +
            "#{beforeTotalStock},#{afterTotalStock},#{beforeLockedStock},#{afterLockedStock},#{remark},now())")
    int insertLog(InventoryLog log);
}
