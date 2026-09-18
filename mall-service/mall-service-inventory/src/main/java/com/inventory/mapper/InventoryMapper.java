package com.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.model.bean.Inventory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    // ⚠️ 方法名说 update，实际是 INSERT——勿据此暴露写接口。
    // 原先它被 InventoryController 的 POST /updateInventory 直接对外，而那条路径既无管理员/归属校验
    // （任何登录用户可对任意 product_id 造库存行），又不写 inventory_log（破坏「所有库存变动都落流水」），
    // 故该接口与对应的 Controller/Service 已删除。
    // 方法本身保留：InventoryMapperConcurrencyTest.seed() 依赖它造初始库存行。
    @Insert("insert into inventory(product_id,user_id,total_stock,locked_stock,available_stock,updated_time)" +
            " values " +
            "(#{productId},#{userId},#{totalStock},#{lockedStock},#{availableStock},now())")
    void updateInventory(Inventory inventory);

    @Select("select * from inventory where product_id=#{productId}")
    Inventory selectByProductId(@Param("productId") Long productId);

    // 锁定库存：available 足够则扣减。返回受影响行数，0 表示库存不足/记录不存在
    // 并发不超卖的保证是条件 UPDATE(available_stock>=qty)，故去掉无意义的 version 自增
    @Update("update inventory set locked_stock=locked_stock+#{qty}, available_stock=available_stock-#{qty} " +
            "where product_id=#{productId} and available_stock>=#{qty}")
    int lockStock(@Param("productId") Long productId, @Param("qty") Integer qty);

    // 释放锁定（扣减失败时回补）
    @Update("update inventory set locked_stock=locked_stock-#{qty}, available_stock=available_stock+#{qty} " +
            "where product_id=#{productId} and locked_stock>=#{qty}")
    int releaseLocked(@Param("productId") Long productId, @Param("qty") Integer qty);
}
