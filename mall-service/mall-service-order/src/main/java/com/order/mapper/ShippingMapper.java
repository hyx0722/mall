package com.order.mapper;

import com.order.bean.Shipping;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShippingMapper {

    @Insert("insert into shipping(ship_no,order_id,seller_id,logistics_company,tracking_no,remark,created_time) " +
            "values(#{shipNo},#{orderId},#{sellerId},#{logisticsCompany},#{trackingNo},#{remark},now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertShipping(Shipping shipping);

    // 某订单里本卖家的发货单（null=本卖家尚未发货）；uk_order_seller 保证至多一条
    @Select("select id,ship_no,order_id,seller_id,logistics_company,tracking_no,remark,created_time " +
            "from shipping where order_id=#{orderId} and seller_id=#{sellerId}")
    Shipping selectByOrderAndSeller(@Param("orderId") Long orderId, @Param("sellerId") Long sellerId);

    // 某订单的全部发货单（买家看物流 / 管理员查发货）
    @Select("select id,ship_no,order_id,seller_id,logistics_company,tracking_no,remark,created_time " +
            "from shipping where order_id=#{orderId} order by id")
    List<Shipping> selectByOrderId(@Param("orderId") Long orderId);

    // 某订单已发货的卖家数（与 order_item join product 算出的总卖家数比对，判断是否「最后一卖」）
    @Select("select count(distinct seller_id) from shipping where order_id=#{orderId}")
    long countShippedSellers(@Param("orderId") Long orderId);
}
