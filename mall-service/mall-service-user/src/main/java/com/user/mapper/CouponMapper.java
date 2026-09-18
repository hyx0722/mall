package com.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.user.bean.Coupon;
import com.user.bean.CouponScope;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    @Insert("insert into coupon(seller_id,name,coupon_type,threshold_amount,discount_amount,discount_rate,"
            + "max_discount_amount,total_count,received_count,start_time,end_time,status,created_time,updated_time) "
            + "values(#{sellerId},#{name},#{couponType},#{thresholdAmount},#{discountAmount},#{discountRate},"
            + "#{maxDiscountAmount},#{totalCount},0,#{startTime},#{endTime},#{status},now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertCoupon(Coupon coupon);

    @Select("select * from coupon where id=#{id}")
    Coupon selectById(@Param("id") Long id);

    /** 商家侧：只取该商家发的券（含停用与已领完），供商家中心管理与启停 */
    @Select("select * from coupon where seller_id=#{sellerId} order by id desc")
    List<Coupon> selectBySeller(@Param("sellerId") Long sellerId);

    // 批量取券定义（「我的券」列表补全用）直接用 BaseMapper 自带的 selectByIds——
    // 不要再声明同名方法：两者擦除后签名相同，会报「名称冲突」

    /**
     * 券中心：**平台券**中启用中、在有效期内、且尚有余量的。
     * 刻意排除商家券（seller_id != 0）——商家券只在其店铺页露出，
     * 否则券中心会被各店铺的券淹没，且领到的券与本店商品对不上。
     */
    @Select("select * from coupon where seller_id=0 and status=1 and now() between start_time and end_time "
            + "and received_count < total_count order by id desc")
    List<Coupon> selectReceivable();

    /** 店铺页：某商家的可领券，条件同券中心但限定 seller_id */
    @Select("select * from coupon where seller_id=#{sellerId} and status=1 "
            + "and now() between start_time and end_time and received_count < total_count order by id desc")
    List<Coupon> selectReceivableBySeller(@Param("sellerId") Long sellerId);

    /** 管理端：全量（含停用与已领完），可按名称模糊 */
    @Select("<script>select * from coupon where 1=1"
            + "<if test='keyword != null and keyword != \"\"'> and name like concat('%',#{keyword},'%')</if>"
            + " order by id desc</script>")
    List<Coupon> selectAll(@Param("keyword") String keyword);

    /** 管理端：改任意券的状态 */
    @Update("update coupon set status=#{status}, updated_time=now() where id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 商家端：改**自己发的**券的状态。带 seller_id 条件即天然防越权——
     * 商家改不动别人的券，也不需要先查一次再判断（那是竞态的来源）。
     */
    @Update("update coupon set status=#{status}, updated_time=now() where id=#{id} and seller_id=#{sellerId}")
    int updateStatusOwned(@Param("id") Long id, @Param("sellerId") Long sellerId,
                          @Param("status") Integer status);

    /**
     * 领券：**条件 UPDATE** 是并发不超发的唯一保证——
     * 把「启用中 + 在有效期内 + 还有余量」全部写进 WHERE，用受影响行数判断是否抢到。
     * 不能写成「先 select 判断余量再 update」：那样两个并发请求会读到同一个余量而双双成功。
     */
    @Update("update coupon set received_count = received_count + 1, updated_time = now() "
            + "where id=#{id} and status=1 and received_count < total_count "
            + "and now() between start_time and end_time")
    int tryReceive(@Param("id") Long id);

    @Select("select * from coupon_scope where coupon_id=#{couponId}")
    List<CouponScope> selectScopes(@Param("couponId") Long couponId);

    @Insert("insert into coupon_scope(coupon_id,scope_type,scope_id,created_time) "
            + "values(#{couponId},#{scopeType},#{scopeId},now())")
    void insertScope(CouponScope scope);
}
