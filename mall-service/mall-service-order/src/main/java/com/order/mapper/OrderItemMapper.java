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
}
