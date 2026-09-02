package com.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付渠道占位配置（前缀 payment）。
 * 真实商户参数请填写到 nacos/common.yaml(mall-service-payment)或本地 application.yml。
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentSdkProperties {

    /** 模拟支付成功测试钩子 */
    private Mock mock = new Mock();

    /** 支付宝渠道 */
    private Alipay alipay = new Alipay();

    /** 微信支付渠道 */
    private Wxpay wxpay = new Wxpay();

    @Data
    public static class Mock {
        /** 演示用模拟支付钩子开关（POST /pay/mock/success） */
        private boolean enabled = false;
    }

    @Data
    public static class Alipay {
        private boolean enabled = false;
        private String gateway = "https://openapi.alipay.com/gateway.do";
        private String appId;
        private String privateKey;
        private String alipayPublicKey;
        private String notifyUrl;
    }

    @Data
    public static class Wxpay {
        private boolean enabled = false;
        private String mchId;
        private String appId;
        private String apiV3Key;
        private String privateKeyPath;
        private String merchantSerialNo;
        private String notifyUrl;
    }
}
