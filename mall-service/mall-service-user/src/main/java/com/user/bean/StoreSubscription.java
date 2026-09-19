package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商店订阅关系：一行 = 「{@code userId} 订阅了 {@code storeId} 这家店」。
 *
 * 「商店」在本仓没有独立实体——**商店就是卖家用户**（商品靠 {@code product.user_id} 归属，
 * 店铺页路由是 {@code /store/:username}），所以 {@code storeId} 指的是 {@code user.id}。
 *
 * 重复订阅由 {@code uk_user_store} 唯一键挡，不做「先查后插」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("store_subscription")
public class StoreSubscription {

    @TableField(value = "id")
    private Long id;

    /** 订阅者 */
    @TableField(value = "user_id")
    private Long userId;

    /** 被订阅的商店（= 卖家用户 id） */
    @TableField(value = "store_id")
    private Long storeId;

    @TableField(value = "created_time")
    private LocalDateTime createdTime;
}
