package com.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.order.bean.Withdraw;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface WithdrawMapper extends BaseMapper<Withdraw> {

    @Insert("insert into withdraw(withdraw_no,seller_id,amount,status,apply_time,created_time,updated_time) "
            + "values(#{withdrawNo},#{sellerId},#{amount},0,now(),now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertWithdraw(Withdraw withdraw);

    /** 申请中（待审核）的金额合计：计算可提现余额时要先把它扣掉，否则能重复申请 */
    @Select("select coalesce(sum(amount),0) from withdraw where seller_id=#{sellerId} and status=0")
    BigDecimal sumPendingBySeller(@Param("sellerId") Long sellerId);

    @Select("select * from withdraw where seller_id=#{sellerId} order by id desc")
    List<Withdraw> selectBySeller(@Param("sellerId") Long sellerId);

    @Select("select * from withdraw where id=#{id}")
    Withdraw selectById(@Param("id") Long id);

    @Select("select * from withdraw where status=#{status} order by id desc")
    List<Withdraw> selectByStatus(@Param("status") Integer status);

    /** 打款：0->1 条件更新，重复审核无副作用 */
    @Update("update withdraw set status=1, audit_user_id=#{auditorId}, audit_time=now(), updated_time=now() "
            + "where id=#{id} and status=0")
    int markPaid(@Param("id") Long id, @Param("auditorId") Long auditorId);

    /** 驳回：0->2。驳回后金额自动回到可提现余额（sumPending 不再计入它） */
    @Update("update withdraw set status=2, audit_user_id=#{auditorId}, audit_time=now(), "
            + "reject_reason=#{reason}, updated_time=now() where id=#{id} and status=0")
    int markRejected(@Param("id") Long id, @Param("auditorId") Long auditorId, @Param("reason") String reason);
}
