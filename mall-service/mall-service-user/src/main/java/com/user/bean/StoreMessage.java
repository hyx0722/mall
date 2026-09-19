package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商店公告：商家发布的一条消息。
 *
 * 发布时会按订阅关系群发一份 {@link Notification}（类型 7）给该店所有订阅者；
 * 同时公告本身在店铺页对**所有人**可见（不只是订阅者），这是刻意的——
 * 订阅的价值在「主动推送」，而不是「把公告藏起来」。
 *
 * 删除公告只删本表的行：已经投递出去的 {@link Notification} 是投递日志，保留。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("store_message")
public class StoreMessage {

    @TableField(value = "id")
    private Long id;

    /** 发布公告的商店（= 卖家用户 id） */
    @TableField(value = "store_id")
    private Long storeId;

    @TableField(value = "content")
    private String content;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;
}
