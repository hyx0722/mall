package com.order.mapper;

import com.model.bean.Order;
import com.order.bean.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderAdminMapper {

    // ---------- 管理员：查看所有订单 ----------

    // 所有订单（无归属条件），可按订单状态过滤；跨库带买家用户名 + 收货快照
    @Select("<script>" +
            "select o.*,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id where 1=1" +
            "<if test='status != null'> and o.order_status=#{status}</if>" +
            " order by o.id desc" +
            "</script>")
    List<Order> findAdminOrders(@Param("status") Integer status);

    // 任意订单头（无归属条件）；跨库带买家用户名 + 收货快照
    @Select("select o.*,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id where o.id=#{id}")
    Order findOrderById(@Param("id") Long id);

    // 管理员：订单明细（含商品名/图/价）
    @Select("select order_id, product_id, product_name, product_image, product_price, quantity, total_price " +
            "from order_item where order_id=#{orderId}")
    List<OrderItem> selectDetailByOrderId(@Param("orderId") Long orderId);
}
