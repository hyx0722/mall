package com.order.mapper;

import com.order.bean.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    @Insert("insert into order_item(order_id,product_id,product_name,product_image,product_price,quantity,total_price,created_time) " +
            "values(#{orderId},#{productId},#{productName},#{productImage},#{productPrice},#{quantity},#{totalPrice},now())")
    int insertOrderItem(OrderItem item);

    @Select("select order_id, product_id, quantity from order_item where order_id=#{orderId}")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    // 商家视角：某订单里属于我（product.user_id=me）的明细行（跨库 product 判定归属）
    @Select("select oi.id, oi.order_id, oi.product_id, oi.product_name, oi.product_image, oi.product_price, oi.quantity, oi.total_price " +
            "from order_item oi " +
            "join mall_service_product.product p on p.id=oi.product_id " +
            "where oi.order_id=#{orderId} and p.user_id=#{userId} order by oi.id")
    List<OrderItem> selectMyItems(@Param("orderId") Long orderId, @Param("userId") Long userId);

    // 管理员：订单明细（含商品名/图/价）
    @Select("select order_id, product_id, product_name, product_image, product_price, quantity, total_price " +
            "from order_item where order_id=#{orderId}")
    List<OrderItem> selectDetailByOrderId(@Param("orderId") Long orderId);
}
