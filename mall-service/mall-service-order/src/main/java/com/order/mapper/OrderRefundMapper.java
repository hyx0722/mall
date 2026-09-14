package com.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.bean.OrderRefund;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 退款申请单 DAO。
 *
 * 审核推进一律用「条件 UPDATE + 受影响行数」防重：WHERE 带上当前 refund_status，
 * 并发的两次审核只有一次能翻转成功，另一次拿到 0 行（与订单状态机同一套惯例）。
 */
@Mapper
public interface OrderRefundMapper extends BaseMapper<OrderRefund> {

    @Insert("insert into order_refund(refund_no,order_id,order_no,user_id,refund_amount,refund_status," +
            "refund_reason,created_time,updated_time) " +
            "values(#{refundNo},#{orderId},#{orderNo},#{userId},#{refundAmount},0," +
            "#{refundReason},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRefund(OrderRefund refund);

    @Select("select * from order_refund where refund_no=#{refundNo}")
    OrderRefund selectByRefundNo(@Param("refundNo") String refundNo);

    /** 某订单最新的退款申请（买家查看进度 / order 侧核对） */
    @Select("select * from order_refund where order_id=#{orderId} order by id desc limit 1")
    OrderRefund selectLatestByOrderId(@Param("orderId") Long orderId);

    /** 买家自己的全部退款申请 */
    @Select("select * from order_refund where user_id=#{userId} order by id desc")
    List<OrderRefund> selectByUserId(@Param("userId") Long userId);

    /** 审核通过：待审核 -> 退款中（打款结果稍后由 pay.refund.success 回来） */
    @Update("update order_refund set refund_status=1, audit_user_id=#{auditorId}, audit_time=now(), " +
            "updated_time=now() where refund_no=#{refundNo} and refund_status=0")
    int markApproved(@Param("refundNo") String refundNo, @Param("auditorId") Long auditorId);

    /** 审核驳回：待审核 -> 已驳回 */
    @Update("update order_refund set refund_status=3, reject_reason=#{rejectReason}, " +
            "audit_user_id=#{auditorId}, audit_time=now(), updated_time=now() " +
            "where refund_no=#{refundNo} and refund_status=0")
    int markRejected(@Param("refundNo") String refundNo, @Param("auditorId") Long auditorId,
                     @Param("rejectReason") String rejectReason);

    /** 退款到账：退款中 -> 已退款（仅推进处于打款中的申请，天然幂等） */
    @Update("update order_refund set refund_status=2, refund_time=now(), updated_time=now() " +
            "where order_id=#{orderId} and refund_status=1")
    int markRefundedByOrderId(@Param("orderId") Long orderId);

    /**
     * 商家侧待审核退款：只返回「整单商品都属于本商家」的申请。
     * 混单（含其它卖家商品）不出现在任何单个商家的列表里，只能由管理员审核——
     * 与「商家整单取消」同规矩（订单是一个整体，单卖家无权替他人决定）。
     */
    @Select("select r.*, u.username as buyer_name " +
            "from order_refund r left join mall_service_user.user u on u.id=r.user_id " +
            "where r.refund_status=0 " +
            "and exists (select 1 from order_item oi join mall_service_product.product p on p.id=oi.product_id " +
            "            where oi.order_id=r.order_id and p.user_id=#{sellerId}) " +
            "and not exists (select 1 from order_item oi join mall_service_product.product p on p.id=oi.product_id " +
            "                where oi.order_id=r.order_id and p.user_id<>#{sellerId}) " +
            "order by r.id desc")
    List<OrderRefund> selectPendingForSeller(@Param("sellerId") Long sellerId);

    /** 管理员侧退款申请：可按审核状态过滤（null=全部） */
    @Select("<script>" +
            "select r.*, u.username as buyer_name, o.order_status as order_status " +
            "from order_refund r " +
            "left join mall_service_user.user u on u.id=r.user_id " +
            "left join orders o on o.id=r.order_id " +
            "where 1=1" +
            "<if test='status != null'> and r.refund_status=#{status}</if>" +
            " order by r.id desc" +
            "</script>")
    List<OrderRefund> selectForAdmin(@Param("status") Integer status);
}
