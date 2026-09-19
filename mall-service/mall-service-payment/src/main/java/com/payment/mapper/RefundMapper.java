package com.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.entity.Refund;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    /** 退款中（0-退款中，1-退款成功，2-退款失败） */
    int STATUS_REFUNDING = 0;

    @Insert("insert into refund(refund_no,order_id,pay_order_id,user_id,refund_amount,refund_status," +
            "refund_reason,created_time,updated_time) " +
            "values(#{refundNo},#{orderId},#{payOrderId},#{userId},#{refundAmount},0," +
            "#{refundReason},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRefund(Refund refund);

    @Select("select * from refund where refund_no=#{refundNo}")
    Refund selectByRefundNo(@Param("refundNo") String refundNo);

    /**
     * 退款对账：捞「停在退款中超过 N 分钟」的退款单。
     *
     * 这些单是「渠道调用抛异常 → 有界重试耗尽 → 落 q.pay.dlq」之后的遗留——
     * 此前没有任何机制会把它们捡回来，订单会永久卡在 5退款中，只能靠人去 DLQ 里翻。
     */
    @Select("select * from refund where refund_status=0 and created_time <= date_sub(now(), interval #{minutes} minute) "
            + "order by id limit 100")
    List<Refund> selectStuckRefunding(@Param("minutes") int minutes);

    /** 悬挂退款单数量（供 mall.pay.refund.stuck 指标用，见 com.payment.metrics.RefundMetrics） */
    @Select("select count(*) from refund where refund_status=0 and created_time <= date_sub(now(), interval #{minutes} minute)")
    long countStuckRefunding(@Param("minutes") int minutes);

    /** 退款成功：退款中 -> 退款成功（条件更新，渠道重复回调天然幂等） */
    @Update("update refund set refund_status=1, refund_time=now(), updated_time=now() " +
            "where refund_no=#{refundNo} and refund_status=0")
    int markRefundSuccess(@Param("refundNo") String refundNo);

    /** 退款失败：退款中 -> 退款失败（审核驳回，或渠道明确拒绝出款） */
    @Update("update refund set refund_status=2, updated_time=now() " +
            "where refund_no=#{refundNo} and refund_status=0")
    int markRefundFailed(@Param("refundNo") String refundNo);
}
