package com.user.service.impl;

import com.model.bean.PageBean;
import com.model.bean.User;
import com.model.exception.BusinessException;
import com.user.bean.Notification;
import com.user.bean.StoreInfoVO;
import com.user.bean.StoreMessage;
import com.user.mapper.StoreMessageMapper;
import com.user.mapper.StoreSubscriptionMapper;
import com.user.mapper.UserMapper;
import com.user.service.NotificationService;
import com.user.service.StoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StoreServiceImpl implements StoreService {

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 单个用户能订阅的商店上限。
     *
     * ⚠️ 这是「先 count 再插」的**检查-使用竞态**：并发双击可能超出上限一两条。
     * 这是刻意接受的——为一个大体量的软上限加锁或加计数列不划算，
     * 而且超出的后果只是多订阅一家店，没有正确性问题。**不要**为此引入分布式锁。
     */
    private static final long MAX_STORE_PER_USER = 100L;

    @Autowired
    StoreSubscriptionMapper subscriptionMapper;
    @Autowired
    StoreMessageMapper messageMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    NotificationService notificationService;

    // ---------- 买家 ----------

    @Override
    public StoreInfoVO info(String username, Long viewerId) {
        Long storeId = requireStoreId(username);
        boolean subscribed = viewerId != null
                && subscriptionMapper.exists(viewerId, storeId) > 0;
        return new StoreInfoVO(username, subscribed, subscriptionMapper.countByStore(storeId));
    }

    @Override
    @Transactional
    public void subscribe(String username, Long userId) {
        Long storeId = requireStoreId(username);
        if (storeId.equals(userId)) {
            throw new BusinessException("不能订阅自己的店铺");
        }
        if (subscriptionMapper.countByUser(userId) >= MAX_STORE_PER_USER) {
            throw new BusinessException("订阅的店铺数量已达上限 " + MAX_STORE_PER_USER);
        }
        // 已订阅时撞唯一键，insertIfAbsent 内部 on duplicate key update 静默吞掉——
        // 连点两下订阅按钮不该弹红字
        subscriptionMapper.insertIfAbsent(userId, storeId);
    }

    @Override
    public void unsubscribe(String username, Long userId) {
        Long storeId = requireStoreId(username);
        // 未订阅时影响 0 行，同样是成功（幂等）
        subscriptionMapper.deleteByUserAndStore(userId, storeId);
    }

    @Override
    public List<String> mySubscriptions(Long userId) {
        List<Long> storeIds = subscriptionMapper.selectStoreIdsByUser(userId);
        if (storeIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameById = userMapper.findUsernamesByIds(storeIds).stream()
                .filter(u -> u.getId() != null && u.getUsername() != null)
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        // 按订阅顺序（倒序）返回，且**跳过已被删除的店**（逻辑外键没有级联，
        // 用户注销后订阅行还在，查不到名字的直接略过而不是返回 null）
        List<String> result = new ArrayList<>();
        for (Long id : storeIds) {
            String name = nameById.get(id);
            if (name != null) {
                result.add(name);
            }
        }
        return result;
    }

    // ---------- 公告：读 ----------

    @Override
    public PageBean<StoreMessage> announcements(String username, Integer page, Integer size) {
        return pageMessages(requireStoreId(username), page, size);
    }

    @Override
    public PageBean<StoreMessage> myAnnouncements(Long sellerId, Integer page, Integer size) {
        return pageMessages(sellerId, page, size);
    }

    private PageBean<StoreMessage> pageMessages(Long storeId, Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, MAX_PAGE_SIZE);
        long total = messageMapper.countByStore(storeId);
        if (total == 0) {
            return new PageBean<>(0L, List.of());
        }
        return new PageBean<>(total, messageMapper.selectByStore(storeId, (p - 1) * s, s));
    }

    // ---------- 公告：写 ----------

    @Override
    @Transactional
    public Long announce(Long sellerId, String content) {
        StoreMessage message = new StoreMessage();
        message.setStoreId(sellerId);
        message.setContent(content);
        messageMapper.insert(message);   // 回填 id

        // 同事务群发：公告落库与订阅者收到通知要么一起成功、要么一起回滚。
        // 两件事都在本库，不需要 outbox。
        String storeName = storeUsername(sellerId);
        notificationService.fanOut(
                sellerId,
                Notification.TYPE_STORE_ANNOUNCEMENT,
                storeName + " 发布了新公告",
                content,
                Notification.REF_STORE,
                sellerId);
        return message.getId();
    }

    @Override
    public void deleteAnnouncement(Long sellerId, Long messageId) {
        if (messageId == null) {
            throw new BusinessException("缺少公告 id");
        }
        // 条件 DELETE 带 store_id 即天然防越权，不必先查一次再判断（那是竞态来源）。
        // 已投递的 notification 是投递日志，**不回收**——级联删等于让商家能抹掉别人的收件箱。
        if (messageMapper.deleteOwned(messageId, sellerId) == 0) {
            throw new BusinessException("公告不存在或不属于你");
        }
    }

    // ---------- 内部 ----------

    /**
     * 用户名 -> 商店 id。查不到即报错，不降级放行——否则任何拼错的用户名都能建出订阅关系。
     * 与 {@code CouponServiceImpl.storeCoupons} 同一做法。
     */
    private Long requireStoreId(String username) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("缺少店铺用户名");
        }
        User store = userMapper.findIdAndPasswordByUserName(username);
        if (store == null || store.getId() == null) {
            throw new BusinessException("商店不存在");
        }
        return store.getId();
    }

    /** 通知文案里的店名。查不到时退回「该店铺」而不是拼出 null */
    private String storeUsername(Long storeId) {
        String name = userMapper.findUsernameById(storeId);
        return name == null ? "该店铺" : name;
    }
}
