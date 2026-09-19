package com.mall.common.outbox;

import java.time.LocalDateTime;

/**
 * 已放弃（status=3）的 outbox 行，供管理端查看。
 *
 * 刻意**不含 payload**：事件体是服务内部的实现细节、可能很大，在列表端点上读 TEXT 列
 * 既浪费带宽也把内部结构暴露给管理端。要排查具体内容应直接查库或看日志。
 *
 * @param id          行号（同时也是投递时的 messageId，见 OutboxServiceImpl#send）
 * @param exchange    目标交换机
 * @param routingKey  目标路由键 —— 重投前应先核对它与队列绑定是否匹配，这才是被放弃的根因
 * @param retryCount  累计重试次数（放弃时已达上限）
 * @param createdTime 原始入箱时间；重投不会改动它，故可据此判断这批事件搁置了多久
 */
public record AbandonedOutbox(Long id,
                              String exchange,
                              String routingKey,
                              Integer retryCount,
                              LocalDateTime createdTime) {
}
