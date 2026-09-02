package com.payment.channel;

import com.payment.entity.PayOrder;

/**
 * 支付渠道抽象（策略模式）：支付宝 / 微信各一实现。
 * 渠道配置为占位（enabled=false）时：
 *  - createPay 返回 PLACEHOLDER 提示，不触达 SDK；
 *  - 通知验签统一失败，保证占位配置绝不误翻转支付状态。
 * SDK 类型只出现在实现类方法局部，不进 Spring bean 字段，确保占位启动不加载 SDK 运行时代码。
 */
public interface PayChannel {

    /** 支付方式：1-支付宝，2-微信 */
    int method();

    /** 是否启用真实渠道（占位配置返回 false） */
    boolean enabled();

    /** 下单：返回给前端的支付参数 */
    PayParams createPay(PayOrder payOrder);
}
