package com.user.mapper;

import com.user.bean.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 站内通知的读写。**刻意不继承 {@code BaseMapper}**：
 * 本表不需要 MyBatis-Plus 的 CRUD，且一旦继承，再声明与基类擦除后签名相同的方法
 * 会报「名称冲突」（{@code CouponMapper} 里有先例注释）。
 *
 * 同理，**不要为此加 {@code @MapperScan}**：那会让 MyBatis-Plus 的自动扫描整体退避，
 * 各服务原有 mapper 全部停止注册，只在启动期炸（{@code OutboxServiceImpl} 类注释有详述）。
 *
 * ── 越权红线 ─────────────────────────────────────────────────────
 * 下面每一个带 {@code user_id} 的方法，那个条件都**不是**可选的：
 * 通知是按收件人隔离的数据，SQL 本身不带 user_id 完全合法、编译也过，
 * 少写一处就是「A 能把 B 的消息标已读/删掉」的横向越权。
 * 特别点名 {@link #markAllRead} 与 {@link #delete} —— 这两个最容易被写成
 * 「update notification set is_read=1 where is_read=0」。
 */
@Mapper
public interface NotificationMapper {

    /**
     * 写一条通知（单收件人）。重投时撞 uk_user_type_ref，由 service 捕获
     * {@code DuplicateKeyException} 后静默返回——重复投递不是错误。
     */
    @Insert("insert into notification(user_id,type,title,content,ref_type,ref_id,store_id,is_read,created_time) "
            + "values(#{userId},#{type},#{title},#{content},#{refType},#{refId},#{storeId},0,now())")
    void insertOne(Notification notification);

    /**
     * 群发给某店的所有订阅者：**一条 SQL**，不是「查订阅者再逐条 insert」。
     *
     * 用 {@code on duplicate key update notification.id=notification.id} 而非
     * {@code insert ignore}：INSERT IGNORE 会把「内容超长」这类错误一并降级成 warning，
     * 静默写出截断/空的通知；本写法只吞唯一键冲突，其余错误照常抛出。
     * （UPDATE 子句里那个列必须写成**带表名**的形式：本 INSERT…SELECT 的源表
     *   {@code store_subscription} 也有 {@code id} 列，不限定表名有歧义风险。）
     *
     * ⚠️ 行数 = 该店订阅者数，无上限。本演示规模可接受；订阅者量大时要改成
     *    分批或走异步 relay，否则一次发公告会在请求线程里锁住 N 行。
     */
    @Insert("insert into notification(user_id,type,title,content,ref_type,ref_id,store_id,is_read,created_time) "
            + "select s.user_id,#{n.type},#{n.title},#{n.content},#{n.refType},#{n.refId},#{n.storeId},0,now() "
            + "from store_subscription s where s.store_id=#{n.storeId} "
            + "on duplicate key update notification.id=notification.id")
    int fanOutToSubscribers(@Param("n") Notification n);

    /**
     * 收件箱分页。
     * {@code types} 为空即「全部」；传入即按类型组筛选（订单 / 商店两个 tab）。
     * {@code isRead} 为 null 即不筛已读状态。
     */
    @Select("<script>select * from notification where user_id=#{userId}"
            + "<if test='isRead != null'> and is_read=#{isRead}</if>"
            + "<if test='types != null and types.size() > 0'> and type in "
            + "<foreach collection='types' item='t' open='(' separator=',' close=')'>#{t}</foreach></if>"
            + " order by id desc limit #{size} offset #{offset}</script>")
    List<Notification> selectPage(@Param("userId") Long userId,
                                  @Param("isRead") Integer isRead,
                                  @Param("types") Collection<Integer> types,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /** 与 {@link #selectPage} 同条件的总数，供 PageBean.total */
    @Select("<script>select count(*) from notification where user_id=#{userId}"
            + "<if test='isRead != null'> and is_read=#{isRead}</if>"
            + "<if test='types != null and types.size() > 0'> and type in "
            + "<foreach collection='types' item='t' open='(' separator=',' close=')'>#{t}</foreach></if>"
            + "</script>")
    long countPage(@Param("userId") Long userId,
                   @Param("isRead") Integer isRead,
                   @Param("types") Collection<Integer> types);

    /** 未读数（顶栏铃铛角标） */
    @Select("select count(*) from notification where user_id=#{userId} and is_read=0")
    long countUnread(@Param("userId") Long userId);

    /** 单条标已读。带 user_id：标不动别人的通知；返回 0 行为「不存在或不属于你」 */
    @Update("update notification set is_read=1 where id=#{id} and user_id=#{userId} and is_read=0")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 全部标已读。
     * ⚠️ {@code and user_id=#{userId}} 一旦漏掉，就是把**所有人**未读的站内信一次清空，
     *    而 SQL 本身完全合法、没有任何报错。
     */
    @Update("update notification set is_read=1 where user_id=#{userId} and is_read=0")
    int markAllRead(@Param("userId") Long userId);

    /** 删除。同样必须带 user_id，理由同 {@link #markAllRead} */
    @Update("delete from notification where id=#{id} and user_id=#{userId}")
    int delete(@Param("id") Long id, @Param("userId") Long userId);
}
