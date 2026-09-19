package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知（「我的消息」的一条）。一行 = 一个收件人的一条消息。
 *
 * ── 写入方 ────────────────────────────────────────────────────────
 * 全部由 user 服务内部产生，没有对外的「创建通知」接口：
 * <ul>
 *   <li>订单链路（类型 1-6）：{@code NotificationListener} / {@code CouponReleaseListener}
 *       消费 MQ 事件时写入；</li>
 *   <li>商店链路（类型 7-9）：发公告 / 上新 / 发券时由 {@link #storeId} 群发给订阅者。</li>
 * </ul>
 *
 * ── 幂等 ──────────────────────────────────────────────────────────
 * MQ 是 at-least-once，且商家可能重复触发，故靠 {@code uk_user_type_ref(user_id,type,ref_id)}
 * 唯一键去重，插入路径捕获 {@code DuplicateKeyException} 后**静默返回**（重复不是错误）。
 *
 * 唯一键必须含 {@code type}：一张订单会合法地产生最多 6 条通知（类型 1-6），
 * 只按 {@code (user_id, ref_id)} 去重会把它们全吞掉。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification")
public class Notification {

    // ---------- 订单链路 ----------
    public static final int TYPE_ORDER_CREATED = 1;
    public static final int TYPE_PAID = 2;
    public static final int TYPE_SHIPPED = 3;
    public static final int TYPE_COMPLETED = 4;
    public static final int TYPE_CANCELED = 5;
    public static final int TYPE_REFUNDED = 6;

    // ---------- 商店链路 ----------
    public static final int TYPE_STORE_ANNOUNCEMENT = 7;
    public static final int TYPE_STORE_NEW_PRODUCT = 8;
    public static final int TYPE_STORE_NEW_COUPON = 9;

    /** 关联业务类型。存字符串而非数字：库里可直接肉眼读懂，新增类型也不用回去改映射表 */
    public static final String REF_ORDER = "ORDER";
    public static final String REF_PRODUCT = "PRODUCT";
    public static final String REF_COUPON = "COUPON";
    public static final String REF_STORE = "STORE";

    /** 无关联时的占位值（**不能是 NULL**，见类注释与 notification 的 DDL 说明） */
    public static final String REF_NONE = "";
    public static final long REF_ID_NONE = 0L;

    /** 非商店来源（订单链路）的占位值 */
    public static final long STORE_NONE = 0L;

    public static final int UNREAD = 0;
    public static final int READ = 1;

    @TableField(value = "id")
    private Long id;

    /** 收件人。所有读写都带这个条件——漏掉即横向越权，见 NotificationMapper */
    @TableField(value = "user_id")
    private Long userId;

    /** {@link #TYPE_ORDER_CREATED} 等 */
    @TableField(value = "type")
    private Integer type;

    @TableField(value = "title")
    private String title;

    @TableField(value = "content")
    private String content;

    /** {@link #REF_ORDER} 等；{@link #REF_NONE} 表示无关联 */
    @TableField(value = "ref_type")
    private String refType;

    /** 依 refType 解释：订单主键 / 商品主键 / 券主键 / 商店ID */
    @TableField(value = "ref_id")
    private Long refId;

    /**
     * 来源商店ID；{@link #STORE_NONE} 表示非商店来源（订单链路）。
     *
     * 刻意**只存 id 不存店名**：列表接口按它批量反查 username 补全即可，
     * 存副本会随改名漂移（与购物车「只存最小事实、读取时补全」同一套）。
     */
    @TableField(value = "store_id")
    private Long storeId;

    /** 0-未读，1-已读 */
    @TableField(value = "is_read")
    private Integer isRead;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;
}
