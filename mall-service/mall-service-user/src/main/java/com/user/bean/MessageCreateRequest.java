package com.user.bean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家发布商店公告的请求体。
 *
 * **没有 storeId 字段**：发布者只取登录态。客户端无从伪造成别家店铺的公告——
 * 与 {@code PublishProductRequest} / {@code CouponCreateRequest} 是同一套做法。
 *
 * {@code @Size(max=500)} 与 {@code notification.content VARCHAR(500)} 对齐：
 * 公告要原样拷进每个订阅者的通知行，这里不卡住的话，超长内容会在扇出那一步
 * 被 MySQL 截断或报错——而扇出用的 {@code on duplicate key update} 不是 INSERT IGNORE，
 * 至少会报错而不是静默写空，但也不该让一个 500 字以上的输入走到那一步。
 */
@Data
public class MessageCreateRequest {

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 500, message = "公告内容不能超过 500 字")
    private String content;
}
