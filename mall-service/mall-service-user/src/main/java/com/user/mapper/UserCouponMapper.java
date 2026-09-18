package com.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.user.bean.UserCoupon;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 领取时插入持有记录。每人每券限领 1 张由 uk_user_coupon 唯一键保证：
     * 并发重复领取会抛 DuplicateKeyException，业务层转成「您已领取过」即可，
     * 不要改成「先查再插」——那正是唯一键要防的竞态。
     */
    @Insert("insert into user_coupon(user_id,coupon_id,status,received_time) "
            + "values(#{userId},#{couponId},0,now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertUserCoupon(UserCoupon userCoupon);

    @Select("select * from user_coupon where id=#{id} and user_id=#{userId}")
    UserCoupon selectByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 我的券：**只查持有记录**，券定义由 service 批量补全。
     *
     * 刻意不在 SQL 里 join 出券字段：那需要把 coupon 的列按 c_* 别名再回填到嵌套属性上，
     * 列名一改就静默映射不上。购物车早就是「只存最小事实、读取时批量补全」的做法
     * （见 docs/domains.md 的购物车一节），这里沿用同一套。
     */
    @Select("<script>select * from user_coupon where user_id=#{userId}"
            + "<if test='status != null'> and status=#{status}</if>"
            + " order by id desc</script>")
    List<UserCoupon> selectMine(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 核销：条件 UPDATE 兜住并发与越权。
     * `status=0` 保证同一张券不会被两笔订单同时用掉；
     * `user_id` 保证不能核销他人的券；
     * 返回受影响行数，0 即「已被用掉 / 不属于你 / 不存在」。
     */
    @Update("update user_coupon set status=1, order_id=#{orderId}, used_time=now() "
            + "where id=#{id} and user_id=#{userId} and status=0")
    int tryUse(@Param("id") Long id, @Param("userId") Long userId, @Param("orderId") Long orderId);

    /**
     * 退券（订单取消 / 退款到账）：**只由 MQ 消费者调用，没有对外接口**。
     *
     * 两处条件缺一不可：
     * - {@code status=1} 让重复投递的事件无副作用（事件可能被重投，必须幂等）；
     * - 按 {@code order_id} 定位而不是由调用方传 userCouponId ——
     *   **绝不能暴露成 HTTP 接口**：只要参数由客户端给，买家就能「用券下单拿到折扣后
     *   立刻把券要回来」重复抵扣。退券的唯一合法触发者是订单进入取消/已退款这两个事实。
     */
    @Update("update user_coupon set status=0, order_id=null, used_time=null "
            + "where order_id=#{orderId} and status=1")
    int releaseByOrderId(@Param("orderId") Long orderId);
}
