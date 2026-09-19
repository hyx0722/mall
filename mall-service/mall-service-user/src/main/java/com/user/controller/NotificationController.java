package com.user.controller;

import com.mall.common.web.Auths;
import com.model.bean.PageBean;
import com.model.bean.Result;
import com.user.bean.Notification;
import com.user.bean.NotificationVO;
import com.user.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 「我的消息」收件箱。对外路径是 {@code /user/message/*}（网关 StripPrefix 掉 /user）。
 *
 * <p><b>没有创建通知的接口</b>：通知是业务事实的产物（订单推进、商店更新），
 * 不是可以手工 POST 出来的资源。写入口只有 MQ 消费者与各业务动作内部的扇出。
 *
 * <p>所有读写的 userId **一律取自登录态**，没有任何一个方法收 userId 参数——
 * 收了就等于允许任何人翻别人的收件箱。这一层不是靠参数校验，是靠「参数根本不存在」。
 */
@RestController
public class NotificationController {

    /** 收件箱 tab -> 通知类型组。「订单」「商店」两个 tab 各对应一组 type */
    private static final List<Integer> ORDER_TYPES = List.of(
            Notification.TYPE_ORDER_CREATED,
            Notification.TYPE_PAID,
            Notification.TYPE_SHIPPED,
            Notification.TYPE_COMPLETED,
            Notification.TYPE_CANCELED,
            Notification.TYPE_REFUNDED);
    private static final List<Integer> STORE_TYPES = List.of(
            Notification.TYPE_STORE_ANNOUNCEMENT,
            Notification.TYPE_STORE_NEW_PRODUCT,
            Notification.TYPE_STORE_NEW_COUPON);

    @Autowired
    NotificationService notificationService;

    /**
     * 收件箱分页。
     *
     * @param category {@code all}（默认）/ {@code order} / {@code store}
     * @param isRead   null 全部 / 0 未读 / 1 已读
     */
    @GetMapping("/message/list")
    public Result<PageBean<NotificationVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Auths.requireLogin();
        List<Integer> types = switch (category == null ? "all" : category) {
            case "order" -> ORDER_TYPES;
            case "store" -> STORE_TYPES;
            // 认不出的分类按「全部」处理：前端某个 tab 改名不该让页面 500
            default -> List.of();
        };
        return Result.success(notificationService.inbox(Auths.currentUserId(), isRead, types, page, size));
    }

    /** 未读数（顶栏铃铛角标）。前端轮询这个接口，**失败必须静默**，见 api/request.js 的 _silent */
    @GetMapping("/message/unreadCount")
    public Result<Long> unreadCount() {
        Auths.requireLogin();
        return Result.success(notificationService.unreadCount(Auths.currentUserId()));
    }

    /** 单条标已读。不属于当前用户时同样返回成功——区分「不存在」与「不是你的」等于泄露前者 */
    @PutMapping("/message/read")
    public Result read(@RequestParam Long id) {
        Auths.requireLogin();
        notificationService.markRead(Auths.currentUserId(), id);
        return Result.success();
    }

    /** 全部标已读，返回本次影响行数 */
    @PutMapping("/message/readAll")
    public Result<Integer> readAll() {
        Auths.requireLogin();
        return Result.success(notificationService.markAllRead(Auths.currentUserId()));
    }

    /** 删除单条 */
    @DeleteMapping("/message/delete")
    public Result delete(@RequestParam Long id) {
        Auths.requireLogin();
        notificationService.delete(Auths.currentUserId(), id);
        return Result.success();
    }
}
