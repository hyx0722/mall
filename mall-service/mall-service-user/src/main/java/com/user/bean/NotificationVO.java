package com.user.bean;

import lombok.Data;

/**
 * 列表接口返回的通知：{@link Notification} 本体 + 补全出来的店铺用户名。
 *
 * 为什么要补：通知行里只存 {@code store_id}（见 {@link Notification#getStoreId()}），
 * 而前端跳店铺页用的是 {@code /store/:username}。补全在 service 里**一次批量**完成，
 * 不放在 SQL 里 join——列名一改就静默映射不上（与「我的券」补全券定义同一理由）。
 */
@Data
public class NotificationVO {

    private Notification notification;

    /**
     * 来源店铺的用户名；非商店来源的通知为 null，此时前端不该渲染「进店」链接。
     */
    private String storeUsername;
}
