package com.payment.channel;

import lombok.Data;

/**
 * 渠道下单结果：前端按 contentType 渲染。
 * - FORM：支付宝电脑网站支付自动提交表单（页面跳支付宝收银台）
 * - CODE_URL：微信 Native 扫码支付二维码内容
 * - PLACEHOLDER：渠道未启用（占位配置），演示请走 /pay/mock/success
 */
@Data
public class PayParams {
    private String contentType;
    private String content;
    private String message;
}
