package com.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.model.bean.Order;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("select id,order_no,user_id,total_amount,order_status,shipping_status from orders " +
            "where user_id=#{userId}")
    List<Order> findAllOrder(@Param("userId") Long userId);

    @Select("select * from orders " +
            "where id=#{id} and user_id=#{userId}")
    Order findDetailOrder(@Param("id") Long id,@Param("userId") Long userId);

    // 创建订单（初始状态 0-待付款），由数据库自增主键回填 id
    @Insert("insert into orders(order_no,user_id,address_id,total_amount,discount_amount,order_status,remark,created_time,updated_time) " +
            "values(#{orderNo},#{userId},#{addressId},#{totalAmount},#{discountAmount},0,#{remark},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrder(Order order);

    // 库存扣减失败回执：待付款 -> 已取消
    @Update("update orders set order_status=4, cancel_time=now() where order_no=#{orderNo} and order_status=0")
    int markDeductFailed(@Param("orderNo") String orderNo);

    // 支付成功回执：待付款 -> 待发货（防重：仅当仍处于待付款）
    @Update("update orders set order_status=1 where id=#{orderId} and order_status=0")
    int markPaid(@Param("orderId") Long orderId);

    // 支付超时取消：待付款超时 -> 已取消（防重：仅当仍处于待付款；返回受影响行数，供是否发释放事件）
    @Select("select id, order_no, user_id from orders " +
            "where order_status=0 and created_time < DATE_SUB(now(), INTERVAL #{minutes} MINUTE) limit 200")
    List<Order> selectOverdueOrders(@Param("minutes") long minutes);

    @Update("update orders set order_status=4, cancel_time=now() where order_no=#{orderNo} and order_status=0")
    int markCancelled(@Param("orderNo") String orderNo);

    // 手动取消（买家本人 + 待付款）：返回受影响行数，0 表示不存在/非本人/已翻转，防并发重复取消
    @Update("update orders set order_status=4, cancel_time=now() where id=#{id} and user_id=#{userId} and order_status=0")
    int cancelUnpaidByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    // 手动取消（商家，归属校验已在 service 完成）：仅待付款，返回受影响行数
    @Update("update orders set order_status=4, cancel_time=now() where id=#{id} and order_status=0")
    int cancelUnpaidById(@Param("id") Long id);

    // 商家：该订单里属于我（product.user_id=me）的明细行数
    @Select("select count(*) from order_item oi " +
            "join mall_service_product.product p on p.id=oi.product_id " +
            "where oi.order_id=#{orderId} and p.user_id=#{userId}")
    long countMyItemLines(@Param("orderId") Long orderId, @Param("userId") Long userId);

    // 商家：该订单里属于其它卖家（product.user_id<>me）的明细行数，>0 即混单不可由本商家单独取消
    @Select("select count(*) from order_item oi " +
            "join mall_service_product.product p on p.id=oi.product_id " +
            "where oi.order_id=#{orderId} and p.user_id<>#{userId}")
    long countForeignItemLines(@Param("orderId") Long orderId, @Param("userId") Long userId);

    // 商家：查看含自己商品的订单（跨库 product 判断归属），带买家名
    @Select("<script>" +
            "select distinct o.id,o.order_no,o.user_id,o.address_id,o.total_amount,o.discount_amount,o.order_status," +
            "o.shipping_status,o.remark,o.created_time,o.updated_time,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id " +
            "where exists (select 1 from order_item oi " +
            "  join mall_service_product.product p on p.id=oi.product_id " +
            "  where oi.order_id=o.id and p.user_id=#{userId}) " +
            "order by o.id desc" +
            "</script>")
    List<Order> findSellerOrders(@Param("userId") Long userId);

    // ---------- 管理员：查看所有订单 ----------

    // 所有订单（无归属条件），可按订单状态过滤；跨库带买家用户名
    @Select("<script>" +
            "select o.id,o.order_no,o.user_id,o.address_id,o.total_amount,o.discount_amount,o.order_status,o.shipping_status,o.remark,o.created_time,o.updated_time,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id where 1=1" +
            "<if test='status != null'> and o.order_status=#{status}</if>" +
            " order by o.id desc" +
            "</script>")
    List<Order> findAdminOrders(@Param("status") Integer status);

    // 任意订单头（无归属条件）；跨库带买家用户名
    @Select("select o.id,o.order_no,o.user_id,o.address_id,o.total_amount,o.discount_amount,o.order_status,o.shipping_status,o.remark,o.created_time,o.updated_time,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id where o.id=#{id}")
    Order findOrderById(@Param("id") Long id);
}
