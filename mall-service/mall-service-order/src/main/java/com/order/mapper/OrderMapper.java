package com.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.model.bean.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("select id,order_no,user_id,total_amount,order_status,shipping_status from orders " +
            "where user_id=#{userId}")
    List<Order> findAllOrder(@Param("userId") Integer userId);

    @Select("select * from orders " +
            "where id=#{id}" +
            "and" +
            "user_id=#{userId}")
    Order findDetailOrder(@Param("id") Integer id,@Param("userId") Integer userId);

}
