package com.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 商店订阅关系。{@code store_id} 指卖家用户 id —— 本仓「商店」就是卖家用户。
 *
 * 订阅/退订都做成**幂等**的：返回受影响行数，0 行不算错误。
 * UI 上双击、重复提交都会重放同一个动作，让调用方去判 409 只会平添麻烦。
 */
@Mapper
public interface StoreSubscriptionMapper {

    /**
     * 订阅。重复订阅撞 uk_user_store 后**静默忽略**（on duplicate key update id=id），
     * 不是抛异常——用户连点两下订阅按钮不该看到红字。
     */
    @Insert("insert into store_subscription(user_id,store_id,created_time) "
            + "values(#{userId},#{storeId},now()) on duplicate key update id=id")
    int insertIfAbsent(@Param("userId") Long userId, @Param("storeId") Long storeId);

    /** 退订。未订阅时影响 0 行，同样是成功 */
    @Delete("delete from store_subscription where user_id=#{userId} and store_id=#{storeId}")
    int deleteByUserAndStore(@Param("userId") Long userId, @Param("storeId") Long storeId);

    @Select("select count(*) from store_subscription where user_id=#{userId} and store_id=#{storeId}")
    long exists(@Param("userId") Long userId, @Param("storeId") Long storeId);

    /** 店铺页展示的订阅人数 */
    @Select("select count(*) from store_subscription where store_id=#{storeId}")
    long countByStore(@Param("storeId") Long storeId);

    /** 「我订阅的店铺」条数，也是订阅上限的判据 */
    @Select("select count(*) from store_subscription where user_id=#{userId}")
    long countByUser(@Param("userId") Long userId);

    /** 我订阅的商店 id 列表（前端用来在店铺页/列表页标「已订阅」） */
    @Select("select store_id from store_subscription where user_id=#{userId} order by id desc")
    List<Long> selectStoreIdsByUser(@Param("userId") Long userId);

    /**
     * 某店的全部订阅者 id。
     *
     * ⚠️ 群发通知**不用**这个方法——那是 {@code NotificationMapper.fanOutToSubscribers}
     * 一条 INSERT…SELECT 干完的事。本方法留给需要逐个处理（如站外推送）的场合，
     * 现在没有调用方；保留是因为它是订阅关系最自然的读法，删掉后下一个人还会再写一遍。
     */
    @Select("<script>select user_id from store_subscription where store_id=#{storeId}"
            + "<if test='excludeUserId != null'> and user_id != #{excludeUserId}</if></script>")
    List<Long> selectUserIdsByStore(@Param("storeId") Long storeId,
                                    @Param("excludeUserId") Long excludeUserId);

    /** 批量判重：这些 storeId 里我订阅了哪些（店铺列表页一次性标出「已订阅」） */
    @Select("<script>select store_id from store_subscription where user_id=#{userId} and store_id in "
            + "<foreach collection='storeIds' item='s' open='(' separator=',' close=')'>#{s}</foreach></script>")
    List<Long> selectSubscribedIn(@Param("userId") Long userId,
                                 @Param("storeIds") Collection<Long> storeIds);
}
