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
public interface InventoryFeignMapper {

    @Select("select * from inventory where product_id=#{id} and user_id=#{userId}")
    Inventory findNumInventory(Product product);

    // 校验/定位商家自己的某条库存（补货前）
    @Select("select * from inventory where product_id=#{productId} and user_id=#{userId}")
    Inventory findByProductIdAndUser(@Param("productId") Long productId, @Param("userId") Long userId);

    // 商品归属校验（跨库直读 product，与 findAllInventory 同款先例）：
    // 初始化库存前必须确认该商品确属调用者，否则任何人都能给他人的商品抢建库存行，
    // 令货主后续 restock 查不到自己的行而无法补货。
    @Select("select count(*) from mall_service_product.product where id=#{productId} and user_id=#{userId}")
    int countOwnedProduct(@Param("productId") Long productId, @Param("userId") Long userId);

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

    /**
     * 商家设置自有商品的预警阈值。带 {@code user_id} 归属条件——
     * 商家改不动别人商品的阈值（与 addStock 同款防越权）。
     */
    @Update("update inventory set warn_threshold=#{threshold}, updated_time=now() "
            + "where product_id=#{productId} and user_id=#{userId}")
    int updateWarnThreshold(@Param("productId") Long productId, @Param("userId") Long userId,
                            @Param("threshold") Integer threshold);

    /**
     * 库存预警扫描：可用库存已跌到阈值（且阈值 > 0），且**不在冷却窗口内**。
     * 冷却靠 last_warn_time，否则每轮扫描都会把同一批商品重复告警一遍。
     */
    @Select("select i.id,i.product_id,i.user_id,i.total_stock,i.locked_stock,i.available_stock,"
            + "i.sales_count,i.warn_threshold,i.last_warn_time,i.created_time,i.updated_time,"
            + " p.name as product_name, u.username as seller_name "
            + "from inventory i "
            + "left join mall_service_product.product p on p.id=i.product_id "
            + "left join mall_service_user.user u on u.id=i.user_id "
            + "where i.warn_threshold > 0 and i.available_stock <= i.warn_threshold "
            + "and (i.last_warn_time is null or i.last_warn_time <= date_sub(now(), interval #{cooldownHours} hour)) "
            + "order by i.available_stock asc limit 200")
    List<Inventory> selectBelowThreshold(@Param("cooldownHours") int cooldownHours);

    /** 记一次告警时间，开启冷却窗口 */
    @Update("update inventory set last_warn_time=now() where id=#{id}")
    int markWarned(@Param("id") Long id);

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
