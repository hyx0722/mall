package com.payment.channel;

import com.payment.entity.PayOrder;

import java.math.BigDecimal;

/**
 * 支付渠道抽象（策略模式）：支付宝 / 微信各一实现。
 * 渠道配置为占位（enabled=false）时：
 *  - createPay 返回 PLACEHOLDER 提示，不触达 SDK；
 *  - 通知验签统一失败，保证占位配置绝不误翻转支付状态；
 *  - refund 直接抛异常，绝不假装退款成功（资金动作宁可失败也不能造假）。
 * SDK 类型只出现在实现类方法局部，不进 Spring bean 字段，确保占位启动不加载 SDK 运行时代码。
 */
public interface PayChannel {

    /** 支付方式：1-支付宝，2-微信 */
    int method();

    /** 是否启用真实渠道（占位配置返回 false） */
    boolean enabled();

    /** 下单：返回给前端的支付参数 */
    PayParams createPay(PayOrder payOrder);

    /**
     * 退款：向渠道发起原路退回。
     *
     * refundNo 作为渠道侧的外层退款单号（支付宝 out_request_no / 微信 out_refund_no），
     * 渠道按它保证幂等——同一退款单重复提交不会重复出款，这也是本仓「重试而非标记失败」
     * 策略成立的前提（见 RefundServiceImpl.executeRefund）。
     *
     * @param payOrder 原支付成功的支付单（提供 out_trade_no 与实付金额）
     * @param refundNo 退款单号（本仓生成，两端对齐）
     * @param amount   退款金额
     * @param reason   退款原因（透传渠道，可空）
     */
    RefundResult refund(PayOrder payOrder, String refundNo, BigDecimal amount, String reason);
}
