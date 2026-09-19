package com.order.mapper;

import com.order.bean.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
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

    /**
     * {@link #selectMyItems} 的批量版本：一次取回一批订单里属于我的全部明细行。
     *
     * 存在的理由是卖家订单列表的 N+1——逐单调用会让查询数随订单数线性增长（1 + 3N），
     * 批量版把「属于我的明细」压成一条查询，调用方按 orderId 分组即可。
     *
     * order by oi.order_id, oi.id：与单订单版同样按 id 升序，保证分组后**每单内部次序不变**，
     * 否则列表页明细的显示顺序会与改动前不一致。
     *
     * ⚠️ 调用方必须先挡空集合：`in ()` 是 SQL 语法错误。
     */
    @Select("<script>" +
            "select oi.id, oi.order_id, oi.product_id, oi.product_name, oi.product_image, oi.product_price, oi.quantity, oi.total_price " +
            "from order_item oi " +
            "join mall_service_product.product p on p.id=oi.product_id " +
            "where p.user_id=#{userId} and oi.order_id in " +
            "<foreach collection='orderIds' item='oid' open='(' separator=',' close=')'>#{oid}</foreach> " +
            "order by oi.order_id, oi.id" +
            "</script>")
    List<OrderItem> selectMyItemsByOrderIds(@Param("orderIds") Collection<Long> orderIds,
                                            @Param("userId") Long userId);

}
