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
}
