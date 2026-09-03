package com.inventory.mapper;

import com.inventory.bean.InventoryLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryLogMapper {

    @Insert("insert into inventory_log(product_id,order_id,change_type,change_quantity," +
            "before_total_stock,after_total_stock,before_locked_stock,after_locked_stock,remark,created_time) " +
            "values(#{productId},#{orderId},#{changeType},#{changeQuantity}," +
            "#{beforeTotalStock},#{afterTotalStock},#{beforeLockedStock},#{afterLockedStock},#{remark},now())")
    int insertLog(InventoryLog log);

    // DB 幂等：该订单该商品是否已有该类型流水（扣减 change_type=3 / 释放 change_type=4）
    @Select("select count(*) from inventory_log where order_id=#{orderId} and product_id=#{productId} and change_type=#{changeType}")
    int countByOrderAndProductType(@Param("orderId") Long orderId,
                                   @Param("productId") Long productId,
                                   @Param("changeType") Integer changeType);
}
