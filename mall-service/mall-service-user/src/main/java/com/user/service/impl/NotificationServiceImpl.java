package com.user.service.impl;

import com.model.bean.PageBean;
import com.model.bean.User;
import com.user.bean.Notification;
import com.user.bean.NotificationVO;
import com.user.mapper.NotificationMapper;
import com.user.mapper.UserMapper;
import com.user.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    /** 分页上限：防止 size=100000 一次把整张表拉回前端 */
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    NotificationMapper notificationMapper;
    @Autowired
    UserMapper userMapper;

    // ---------- 写入 ----------

    @Override
    public void record(Long userId, int type, String title, String content,
                       String refType, Long refId, Long storeId) {
        if (userId == null) {
            // 事件体缺 userId 时写不了通知，但也不该让消费失败——那会把一条本可忽略的
            // 脏消息升级成重试 3 次 + 落 DLQ
            log.warn("[notification] 缺少收件人 userId，跳过。type={} refType={} refId={}", type, refType, refId);
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType == null ? Notification.REF_NONE : refType);
        // 唯一键的组成部分，**不能是 null**：MySQL 视多个 NULL 为互不相同，去重会静默失效
        n.setRefId(refId == null ? Notification.REF_ID_NONE : refId);
        n.setStoreId(storeId == null ? Notification.STORE_NONE : storeId);
        try {
            notificationMapper.insertOne(n);
        } catch (DuplicateKeyException e) {
            // 重投/重复触发。这是**预期路径**，不是错误：MQ 是 at-least-once，
            // 商家也可能重复点。必须吞掉——否则重试耗尽落 DLQ，真毒消息会被埋在里面。
            log.debug("[notification] 通知已存在，跳过。userId={} type={} refId={}", userId, type, refId);
        }
    }

    @Override
    public void fanOut(Long storeId, int type, String title, String content, String refType, Long refId) {
        if (storeId == null || storeId == Notification.STORE_NONE) {
            // store_id=0 是「平台」的占位值（如管理员发的平台券），订阅表里不可能有 store_id=0
            // 的订阅者，但显式挡一下比依赖「查出来是空集」更清楚
            return;
        }
        Notification n = new Notification();
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType == null ? Notification.REF_NONE : refType);
        n.setRefId(refId == null ? Notification.REF_ID_NONE : refId);
        n.setStoreId(storeId);
        int affected = notificationMapper.fanOutToSubscribers(n);
        log.info("[notification] 商店 {} 群发完成，影响 {} 行。type={} refId={}", storeId, affected, type, refId);
    }

    // ---------- 收件箱 ----------

    @Override
    public PageBean<NotificationVO> inbox(Long userId, Integer isRead, Collection<Integer> types,
                                          Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, MAX_PAGE_SIZE);
        long total = notificationMapper.countPage(userId, isRead, types);
        if (total == 0) {
            return new PageBean<>(0L, List.of());
        }
        List<Notification> rows = notificationMapper.selectPage(userId, isRead, types, (p - 1) * s, s);
        return new PageBean<>(total, withStoreUsername(rows));
    }

    /**
     * 批量补全店铺用户名。
     *
     * 通知行里只存 store_id（见 {@link Notification#getStoreId()}），而前端跳店铺页用的是
     * {@code /store/:username}。**一次性批量查**，不要在循环里逐个查库。
     *
     * 与「我的券」补全券定义是同一套：只存最小事实，读取时补全。
     */
    private List<NotificationVO> withStoreUsername(List<Notification> rows) {
        Set<Long> storeIds = rows.stream()
                .map(Notification::getStoreId)
                .filter(id -> id != null && id != Notification.STORE_NONE)
                .collect(Collectors.toSet());
        Map<Long, String> nameById = new HashMap<>();
        if (!storeIds.isEmpty()) {
            List<User> stores = userMapper.findUsernamesByIds(storeIds);
            nameById = stores.stream()
                    .filter(u -> u.getId() != null && u.getUsername() != null)
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        }
        final Map<Long, String> names = nameById;
        return rows.stream().map(n -> {
            NotificationVO vo = new NotificationVO();
            vo.setNotification(n);
            vo.setStoreUsername(names.get(n.getStoreId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    public boolean markRead(Long userId, Long id) {
        if (id == null) {
            return false;
        }
        // 影响 0 行的三种可能：不存在 / 不属于你 / 已经是已读。前两种不该区分出来
        // （区分等于告诉调用方「这个 id 存在但不是你的」），第三种本来就无需处理，
        // 故统一返回 false，由 controller 转成同样的结果。
        return notificationMapper.markRead(id, userId) > 0;
    }

    @Override
    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId);
    }

    @Override
    public boolean delete(Long userId, Long id) {
        if (id == null) {
            return false;
        }
        return notificationMapper.delete(id, userId) > 0;
    }
}
