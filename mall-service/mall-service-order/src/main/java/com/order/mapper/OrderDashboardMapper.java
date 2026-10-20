package com.order.mapper;

import com.model.bean.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 管理后台看板（order 侧）。
 *
 * <p>统计口径统一：**GMV / 支付订单数只算「已支付」状态**（1待发货、2待收货、3已完成、
 * 5退款中、6已退款），排除 0待付款与 4已取消。金额用 {@code total_amount - discount_amount}
 * （实付），与支付侧 {@code payableAmount}、退款侧 {@code netPaidAmount} 保持同一口径。</p>
 *
 * <p>这里刻意不跨库 join 商品/用户表：看板只需要订单与明细快照，商品名在
 * {@code order_item.product_name} 里已有快照，避免再给 order 库增加跨库依赖。</p>
 */
@Mapper
public interface OrderDashboardMapper {

    /** 顶部概览：一次聚合查询，避免前端拿多个 count 再相加 */
    @Select("""
            select count(*)                                                                 as totalOrders,
                   coalesce(sum(case when order_status in (1,2,3,5,6) then 1 else 0 end), 0) as paidOrders,
                   coalesce(sum(case when order_status in (1,2,3,5,6)
                                     then total_amount - discount_amount else 0 end), 0)   as gmv,
                   coalesce(sum(case when order_status = 0 then 1 else 0 end), 0)           as waitPay,
                   coalesce(sum(case when order_status = 1 then 1 else 0 end), 0)           as waitShip,
                   coalesce(sum(case when order_status = 2 then 1 else 0 end), 0)           as waitReceive,
                   coalesce(sum(case when order_status = 5 then 1 else 0 end), 0)           as refunding,
                   coalesce(sum(case when order_status = 4 then 1 else 0 end), 0)           as cancelled,
                   coalesce(sum(case when order_status = 6 then 1 else 0 end), 0)           as refunded,
                   coalesce(sum(case when date(created_time) = curdate() then 1 else 0 end), 0) as todayOrders,
                   coalesce(sum(case when date(created_time) = curdate()
                                      and order_status in (1,2,3,5,6)
                                     then total_amount - discount_amount else 0 end), 0)   as todayGmv
            from orders
            """)
    Map<String, Object> summary();

    /** 近 N 天趋势：按订单创建日聚合；空缺日由 service 补 0 */
    @Select("""
            select date(created_time) as day,
                   count(*)           as orderCount,
                   coalesce(sum(case when order_status in (1,2,3,5,6)
                                     then total_amount - discount_amount else 0 end), 0) as gmv
            from orders
            where created_time >= date_sub(curdate(), interval #{days} day)
            group by date(created_time)
            order by day
            """)
    List<Map<String, Object>> trend(@Param("days") int days);

    /** 订单状态分布（含 0/4，前端饼图用） */
    @Select("""
            select order_status as code, count(*) as cnt
            from orders
            group by order_status
            order by order_status
            """)
    List<Map<String, Object>> statusCounts();

    /** 热销商品：只统计已支付状态，近 N 天；按件数排序 */
    @Select("""
            select oi.product_id   as productId,
                   oi.product_name as productName,
                   sum(oi.quantity) as quantity,
                   sum(oi.total_price) as amount
            from order_item oi
            join orders o on o.id = oi.order_id
            where o.order_status in (1,2,3,5,6)
              and o.created_time >= date_sub(curdate(), interval #{days} day)
            group by oi.product_id, oi.product_name
            order by quantity desc
            limit #{limit}
            """)
    List<Map<String, Object>> topProducts(@Param("days") int days, @Param("limit") int limit);

    /** 最近订单（看板下方的流水），只取展示需要的列 */
    @Select("""
            select id, order_no, user_id, total_amount, discount_amount, order_status, created_time
            from orders
            order by id desc
            limit #{limit}
            """)
    List<Order> recentOrders(@Param("limit") int limit);

    /** 退款待办与累计退款金额 */
    @Select("""
            select coalesce(sum(case when refund_status = 0 then 1 else 0 end), 0) as pending,
                   coalesce(sum(case when refund_status = 2 then refund_amount else 0 end), 0) as refundedAmount
            from order_refund
            """)
    Map<String, Object> refundSummary();
}
