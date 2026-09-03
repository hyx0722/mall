package com.model.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事务性发件箱（outbox）：与业务状态变更在同一个本地事务中写入，
 * 由各服务独立的 relay 定时投递到 RabbitMQ，解决「业务落库与事件发布非原子」。
 * order / payment 两库各建同名表复用本实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("outbox")
public class Outbox {

    @TableField("id")
    private Long id;

    /** 目标交换机（如 mall.order.exchange / mall.order.delay.exchange） */
    @TableField("exchange")
    private String exchange;

    /** 目标路由键 */
    @TableField("routing_key")
    private String routingKey;

    /** 事件 JSON 原文（relay 原样发送，contentType=application/json） */
    @TableField("payload")
    private String payload;

    /** 0-待发送 1-已发送 */
    @TableField("status")
    private Integer status;

    @TableField("retry_count")
    private Integer retryCount;

    /** 非空：发送到延迟交换机时附加 per-message TTL（毫秒） */
    @TableField("delay_ms")
    private Long delayMs;

    @TableField("created_time")
    private LocalDateTime createdTime;

    @TableField("sent_time")
    private LocalDateTime sentTime;
}
