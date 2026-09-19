package com.user.service;

import com.model.bean.PageBean;
import com.user.bean.Notification;
import com.user.bean.NotificationVO;

import java.util.Collection;

/**
 * 站内通知：写入（内部）+ 收件箱（对外）。
 *
 * 写入侧**没有对外的 HTTP 接口**——通知是业务事实的产物，不是可以手工创建的资源。
 * 唯一的两个入口是 MQ 消费者（订单链路）与各业务动作里的扇出（商店链路）。
 */
public interface NotificationService {

    // ---------- 写入（仅服务内部调用） ----------

    /**
     * 写一条通知给单个收件人。
     *
     * **幂等**：撞 {@code uk_user_type_ref} 时捕获 {@code DuplicateKeyException} 后
     * 静默返回，不抛异常。MQ 是 at-least-once，重复投递是常态而非故障；
     * 若让它抛，重试耗尽会落 DLQ 并掩盖真正的毒消息。
     *
     * <p>⚠️ <b>本方法只应在**没有外层事务**时调用</b>（现状如此：两个调用点都是 MQ 监听方法）。
     * 它靠捕获 {@code DuplicateKeyException} 实现幂等，而事务内被捕获的约束冲突会让
     * 事务语义变得含糊。需要「在业务事务里群发」时请用 {@link #fanOut}——
     * 那条路径走 {@code on duplicate key update}，重复键根本不会抛异常。
     *
     * @param userId  收件人
     * @param type    {@link Notification#TYPE_ORDER_CREATED} 等
     * @param refType {@link Notification#REF_ORDER} 等；无关联传 {@link Notification#REF_NONE}
     * @param refId   关联主键；无关联传 {@link Notification#REF_ID_NONE}（**不能传 null**）
     * @param storeId 来源商店；非商店来源传 {@link Notification#STORE_NONE}
     */
    void record(Long userId, int type, String title, String content,
                String refType, Long refId, Long storeId);

    /**
     * 群发给某店的所有订阅者，**一条 SQL** 写完。
     *
     * 由发公告 / 上新 / 发新券三处调用，与触发的业务动作同事务（同库，一起成功或一起回滚）。
     * 幂等同上：重复触发不会重复写。
     *
     * @param storeId 商店（= 卖家用户 id）
     */
    void fanOut(Long storeId, int type, String title, String content, String refType, Long refId);

    // ---------- 收件箱（对外） ----------

    /**
     * 收件箱分页。
     *
     * @param userId 收件人，**必传**——所有查询都按它隔离
     * @param isRead null 全部 / 0 未读 / 1 已读
     * @param types  null 或空即全部；传订单那组或商店那组即按 tab 筛选
     */
    PageBean<NotificationVO> inbox(Long userId, Integer isRead, Collection<Integer> types,
                                   Integer page, Integer size);

    /** 未读数，顶栏铃铛角标用 */
    long unreadCount(Long userId);

    /** 单条标已读。不属于该用户时返回 false（不报错，避免泄露「这个 id 存在」） */
    boolean markRead(Long userId, Long id);

    /** 全部标已读，返回本次影响行数 */
    int markAllRead(Long userId);

    /** 删除单条。不属于该用户时返回 false */
    boolean delete(Long userId, Long id);
}
