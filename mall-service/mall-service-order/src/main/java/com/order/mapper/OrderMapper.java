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

    // 我的订单列表（全列：含收货快照/发货时间等，供买家端展示）
    @Select("select * from orders where user_id=#{userId} order by id desc")
    List<Order> findAllOrder(@Param("userId") Long userId);

    // 我的订单详情（归属条件 id AND user_id）
    @Select("select * from orders " +
            "where id=#{id} and user_id=#{userId}")
    Order findDetailOrder(@Param("id") Long id,@Param("userId") Long userId);

    // 发货并发控制：锁定订单行（必须是相关事务里第一条 DB 语句，避免 RR 读快照固化导致判断失真）
    @Select("select * from orders where id=#{id} for update")
    Order selectForUpdate(@Param("id") Long id);

    // 买家确认收货并发控制：锁定订单行且限定归属（id AND user_id）
    @Select("select * from orders where id=#{id} and user_id=#{userId} for update")
    Order selectOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

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

    // 买家确认收货：待收货 -> 已完成（防重：仅当仍处于待收货；归属 id AND user_id）
    @Update("update orders set order_status=3, shipping_status=2, complete_time=now() " +
            "where id=#{id} and user_id=#{userId} and order_status=2")
    int receiveOrder(@Param("id") Long id, @Param("userId") Long userId);

    // 该订单涉及的卖家总数（order_item 跨库 join product 去重）
    @Select("select count(distinct p.user_id) from order_item oi " +
            "join mall_service_product.product p on p.id=oi.product_id " +
            "where oi.order_id=#{orderId}")
    long countTotalSellers(@Param("orderId") Long orderId);

    // 该订单全部卖家都已发货：待发货 -> 待收货（防重：仅当仍处于待发货）
    @Update("update orders set order_status=2, shipping_status=1, shipping_time=now() " +
            "where id=#{orderId} and order_status=1")
    int markFullyShipped(@Param("orderId") Long orderId);

    // 补收货人快照（仅当仍未快照时写入，防并发覆盖）
    @Update("update orders set receiver_name=#{name}, receiver_phone=#{phone}, receiver_address=#{address} " +
            "where id=#{orderId} and receiver_name is null")
    int snapshotReceiver(@Param("orderId") Long orderId, @Param("name") String name,
                         @Param("phone") String phone, @Param("address") String address);

    // 跨库读买家收货地址（支付/发货时冻结快照）：按下单时选定的地址
    // receiver_address 列宽 255，拼接地址需 LEFT 截断防溢出
    @Select("select receiver_name as receiverName, receiver_phone as receiverPhone, " +
            "left(concat_ws(' ', province, city, district, detail_address), 255) as receiverAddress " +
            "from mall_service_user.user_address where id=#{addressId} and user_id=#{userId}")
    Order selectAddressByIdAndUser(@Param("addressId") Long addressId, @Param("userId") Long userId);

    // 跨库读买家收货地址兜底：默认地址优先，否则最早一条（addressId 失效/未填时）
    @Select("select receiver_name as receiverName, receiver_phone as receiverPhone, " +
            "left(concat_ws(' ', province, city, district, detail_address), 255) as receiverAddress " +
            "from mall_service_user.user_address where user_id=#{userId} " +
            "order by is_default desc, id asc limit 1")
    Order selectDefaultAddressByUser(@Param("userId") Long userId);

    // 支付超时取消：待付款超时 -> 已取消（防重：仅当仍处于待付款；返回受影响行数，供是否发释放事件）
    @Select("select id, order_no, user_id from orders " +
            "where order_status=0 and created_time < DATE_SUB(now(), INTERVAL #{minutes} MINUTE) limit 200")
    List<Order> selectOverdueOrders(@Param("minutes") long minutes);

    // 按业务订单号查完整订单（统一取消漏斗/超时消费用）
    @Select("select * from orders where order_no=#{orderNo}")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

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

    // 商家：查看含自己商品的订单（跨库 product 判断归属），带买家名 + 收货快照
    @Select("<script>" +
            "select distinct o.*,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id " +
            "where exists (select 1 from order_item oi " +
            "  join mall_service_product.product p on p.id=oi.product_id " +
            "  where oi.order_id=o.id and p.user_id=#{userId}) " +
            "order by o.id desc" +
            "</script>")
    List<Order> findSellerOrders(@Param("userId") Long userId);

    // 任意订单头（无归属条件）；跨库带买家用户名 + 收货快照
    @Select("select o.*,u.username as buyer_name " +
            "from orders o left join mall_service_user.user u on u.id=o.user_id where o.id=#{id}")
    Order findOrderById(@Param("id") Long id);
}
