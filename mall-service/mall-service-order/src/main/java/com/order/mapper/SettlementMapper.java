package com.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.bean.Settlement;
import com.order.bean.SettlementLine;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SettlementMapper extends BaseMapper<Settlement> {

    /**
     * 取该订单各行 + 卖家 id（跨库直读 mall_service_product.product，与订单收款快照同款先例）。
     * 商品已被物理删除时 seller_id 为 null，调用方需跳过该行（无法确定钱该给谁）。
     */
    @Select("select oi.id as order_item_id, oi.product_id, oi.quantity, oi.total_price, "
            + "p.user_id as seller_id "
            + "from order_item oi left join mall_service_product.product p on p.id = oi.product_id "
            + "where oi.order_id = #{orderId} order by oi.id")
    List<SettlementLine> selectSettlementLines(@Param("orderId") Long orderId);

    /**
     * 记一笔结算。**不用 INSERT IGNORE**：那会把其它错误（如字段超长、非空约束）一并吞掉。
     * 重投造成的唯一键冲突由调用方按行捕获 DuplicateKeyException 忽略——这样「已记过账」
     * 与「真的写不进去」能被区分开。
     */
    @Insert("insert into settlement(order_id,order_no,order_item_id,seller_id,product_id,"
            + "gross_amount,discount_amount,commission_amount,net_amount,status,created_time,updated_time) "
            + "values(#{orderId},#{orderNo},#{orderItemId},#{sellerId},#{productId},"
            + "#{grossAmount},#{discountAmount},#{commissionAmount},#{netAmount},0,now(),now())")
    void insertSettlement(Settlement settlement);

    /** 卖家视角：按状态汇总应结金额 */
    @Select("select coalesce(sum(net_amount),0) from settlement where seller_id=#{sellerId} and status=#{status}")
    BigDecimal sumNetBySellerAndStatus(@Param("sellerId") Long sellerId, @Param("status") Integer status);

    /**
     * 账期 T+N：把已过 N 天的待结算明细转为可提现。
     * 用结算明细的 created_time 而不是订单完成时间——明细就是那一刻生成的，两者等价且不用回查订单。
     */
    @Update("update settlement set status=1, updated_time=now() "
            + "where status=0 and created_time <= date_sub(now(), interval #{days} day)")
    int markWithdrawable(@Param("days") int days);

    /**
     * 提现打款成功：把该商家在**申请时刻之前**的可提现明细置为已提现。
     * 以 apply_time 为界（而不是「全部可提现」），否则申请之后新结算进来的钱会被一起标记掉。
     */
    @Update("update settlement set status=2, updated_time=now() "
            + "where seller_id=#{sellerId} and status=1 and created_time <= #{beforeTime}")
    int markWithdrawn(@Param("sellerId") Long sellerId, @Param("beforeTime") LocalDateTime beforeTime);

    @Select("select * from settlement where seller_id=#{sellerId} order by id desc")
    List<Settlement> selectBySeller(@Param("sellerId") Long sellerId);
}
