package com.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.entity.PayOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    @Select("select * from pay_order where pay_no=#{payNo}")
    PayOrder selectByPayNo(@Param("payNo") String payNo);

    /** 同一订单同渠道的存活待支付单（payment_status=0），避免重复创建 */
    @Select("select * from pay_order where order_id=#{orderId} and payment_method=#{paymentMethod} and payment_status=0 " +
            "limit 1")
    PayOrder selectLiveByOrderAndMethod(@Param("orderId") Long orderId, @Param("paymentMethod") Integer paymentMethod);

    @Insert("insert into pay_order(pay_no,order_id,user_id,pay_amount,payment_method,payment_status,expire_time,created_time,updated_time) " +
            "values(#{payNo},#{orderId},#{userId},#{payAmount},#{paymentMethod},0,#{expireTime},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPayOrder(PayOrder payOrder);

    /** 支付成功：待支付 -> 支付成功（防重：仅当仍处于待支付），并回写流水号与支付时间 */
    @Update("update pay_order set payment_status=1, transaction_id=#{transactionId}, pay_time=now(), updated_time=now() " +
            "where pay_no=#{payNo} and payment_status=0")
    int markPaid(@Param("payNo") String payNo, @Param("transactionId") String transactionId);

    /** 订单取消：把该订单仍待支付的支付单置为关闭（payment_status=0->3），已支付的不动 */
    @Update("update pay_order set payment_status=3, updated_time=now() " +
            "where order_id=#{orderId} and payment_status=0")
    int markClosedByOrderId(@Param("orderId") Long orderId);
}
