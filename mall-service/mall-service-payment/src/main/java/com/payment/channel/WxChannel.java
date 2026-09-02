package com.payment.channel;

import com.payment.config.PaymentSdkProperties;
import com.payment.entity.PayOrder;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Map;

/**
 * 微信支付渠道：Native 扫码支付（APIv3，官方 wechatpay-java SDK）。
 * 占位配置（enabled=false）时不实例化 SDK 客户端：下单返回 PLACEHOLDER、回调验签恒失败。
 * 填入真实商户参数并 enabled=true 后走真实 Native 下单。
 *
 * 说明：SDK 客户端对象只在方法内按需创建（不进 Spring bean 字段），占位启动不触达 SDK 运行时代码。
 */
@Slf4j
@Component
public class WxChannel implements PayChannel {

    private static final String SUCCESS = "SUCCESS";

    private final PaymentSdkProperties.Wxpay props;

    public WxChannel(PaymentSdkProperties props) {
        this.props = props.getWxpay();
    }

    @Override
    public int method() {
        return 2;
    }

    @Override
    public boolean enabled() {
        return props.isEnabled();
    }

    @Override
    public PayParams createPay(PayOrder payOrder) {
        PayParams params = new PayParams();
        if (!props.isEnabled()) {
            params.setContentType("PLACEHOLDER");
            params.setContent("微信支付渠道未启用（占位配置）。演示请调用 /pay/mock/success。");
            params.setMessage("请配置微信商户参数后重试");
            return params;
        }
        try {
            RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(props.getMchId())
                    .privateKeyFromPath(props.getPrivateKeyPath())
                    .merchantSerialNumber(props.getMerchantSerialNo())
                    .apiV3Key(props.getApiV3Key())
                    .build();
            NativePayService service = new NativePayService.Builder().config(config).build();

            PrepayRequest request = new PrepayRequest();
            request.setAppid(props.getAppId());
            request.setMchid(props.getMchId());
            request.setDescription("商城订单-" + payOrder.getOrderId());
            request.setOutTradeNo(payOrder.getPayNo());
            request.setNotifyUrl(props.getNotifyUrl());
            Amount amount = new Amount();
            amount.setTotal(payOrder.getPayAmount()
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue());
            request.setAmount(amount);

            PrepayResponse response = service.prepay(request);
            params.setContentType("CODE_URL");
            params.setContent(response.getCodeUrl());
            params.setMessage("请使用微信扫码完成支付");
            return params;
        } catch (Exception e) {
            log.error("[wxpay] 下单失败 payNo={}", payOrder.getPayNo(), e);
            throw new IllegalStateException("微信下单失败：" + e.getMessage());
        }
    }

    /**
     * 微信支付异步通知：验签 + 解密后取支付单号/流水号。
     * 占位配置恒返回 null（无可用商户证书/密钥），回调不会推进支付状态。
     */
    public NotifyResult handleNotify(Map<String, String> headers, String body) {
        if (!props.isEnabled() || headers == null || body == null) {
            return null;
        }
        try {
            RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                    .merchantId(props.getMchId())
                    .privateKeyFromPath(props.getPrivateKeyPath())
                    .merchantSerialNumber(props.getMerchantSerialNo())
                    .apiV3Key(props.getApiV3Key())
                    .build();
            com.wechat.pay.java.core.notification.RequestParam requestParam =
                    new com.wechat.pay.java.core.notification.RequestParam.Builder()
                            .serialNumber(headers.get("Wechatpay-Serial"))
                            .timestamp(headers.get("Wechatpay-Timestamp"))
                            .nonce(headers.get("Wechatpay-Nonce"))
                            .signature(headers.get("Wechatpay-Signature"))
                            .body(body)
                            .build();
            Transaction transaction = new com.wechat.pay.java.core.notification.NotificationParser(config)
                    .parse(requestParam, Transaction.class);
            if (transaction == null || !SUCCESS.equals(transaction.getTradeState().name())) {
                return null;
            }
            NotifyResult result = new NotifyResult();
            result.setPayNo(transaction.getOutTradeNo());
            result.setTransactionId(transaction.getTransactionId());
            return result;
        } catch (Exception e) {
            log.error("[wxpay] 回调验签/解密异常", e);
            return null;
        }
    }
}
