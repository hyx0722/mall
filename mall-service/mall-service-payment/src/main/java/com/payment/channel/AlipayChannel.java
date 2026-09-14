package com.payment.channel;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.payment.config.PaymentSdkProperties;
import com.payment.entity.PayOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝渠道：电脑网站支付（AlipayTradePagePay）。
 * 占位配置（enabled=false）时不实例化 SDK，下单返回 PLACEHOLDER、回调验签恒失败。
 * 填入真实商户参数并 enabled=true 后即走真实下单/验签。
 */
@Slf4j
@Component
public class AlipayChannel implements PayChannel {

    private static final String CHARSET = "UTF-8";
    private static final String FORMAT = "json";
    private static final String SIGN_TYPE = "RSA2";
    /** 支付宝交易成功状态 */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";

    private final PaymentSdkProperties.Alipay props;

    public AlipayChannel(PaymentSdkProperties props) {
        this.props = props.getAlipay();
    }

    @Override
    public int method() {
        return 1;
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
            params.setContent("支付宝渠道未启用（占位配置）。演示请调用 /pay/mock/success。");
            params.setMessage("请配置支付宝商户参数后重试");
            return params;
        }
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(props.getGateway(), props.getAppId(),
                    props.getPrivateKey(), FORMAT, CHARSET, props.getAlipayPublicKey(), SIGN_TYPE);
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(payOrder.getPayNo());
            model.setTotalAmount(payOrder.getPayAmount().toPlainString());
            model.setSubject("商城订单-" + payOrder.getOrderId());
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            model.setTimeoutExpress("30m");

            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(props.getNotifyUrl());
            request.setBizModel(model);
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            params.setContentType("FORM");
            params.setContent(response.getBody());
            params.setMessage("请使用支付宝扫码/登录完成支付");
            return params;
        } catch (AlipayApiException e) {
            log.error("[alipay] 下单失败 payNo={}", payOrder.getPayNo(), e);
            throw new IllegalStateException("支付宝下单失败：" + e.getErrMsg());
        }
    }

    /**
     * 支付宝退款（AlipayTradeRefund）。
     * out_request_no 用退款单号，支付宝据此保证同一退款单重复提交不会重复出款——
     * 这是上游「渠道失败就重试」策略的安全前提。
     */
    @Override
    public RefundResult refund(PayOrder payOrder, String refundNo, BigDecimal amount, String reason) {
        if (!props.isEnabled()) {
            // 占位配置下绝不假装退款成功：抛出让消息重试并最终落 DLQ，由人工介入
            throw new IllegalStateException("支付宝渠道未启用（占位配置），无法退款 refundNo=" + refundNo);
        }
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(props.getGateway(), props.getAppId(),
                    props.getPrivateKey(), FORMAT, CHARSET, props.getAlipayPublicKey(), SIGN_TYPE);
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(payOrder.getPayNo());
            model.setRefundAmount(amount.toPlainString());
            model.setOutRequestNo(refundNo);
            if (reason != null && !reason.isBlank()) {
                model.setRefundReason(reason);
            }
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            request.setBizModel(model);
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                log.info("[alipay] 退款成功 refundNo={} tradeNo={}", refundNo, response.getTradeNo());
                return RefundResult.ok(response.getTradeNo());
            }
            log.warn("[alipay] 退款失败 refundNo={} code={} subMsg={}", refundNo, response.getCode(), response.getSubMsg());
            return RefundResult.fail("支付宝退款失败：" + response.getSubMsg());
        } catch (AlipayApiException e) {
            log.error("[alipay] 退款异常 refundNo={}", refundNo, e);
            throw new IllegalStateException("支付宝退款异常：" + e.getErrMsg(), e);
        }
    }

    /**
     * 支付宝异步通知验签并提取支付单号/流水号；验签失败或非成功交易返回 null。
     * 占位配置（无支付宝公钥）恒返回 null，回调不会推进支付状态。
     */
    public NotifyResult handleNotify(Map<String, String> params) {
        if (!props.isEnabled() || params == null) {
            return null;
        }
        String tradeStatus = params.get("trade_status");
        if (!TRADE_SUCCESS.equals(tradeStatus) && !TRADE_FINISHED.equals(tradeStatus)) {
            return null;
        }
        try {
            boolean ok = AlipaySignature.rsaCheckV1(params, props.getAlipayPublicKey(), CHARSET, SIGN_TYPE);
            if (!ok) {
                log.warn("[alipay] 回调验签失败 out_trade_no={}", params.get("out_trade_no"));
                return null;
            }
            NotifyResult result = new NotifyResult();
            result.setPayNo(params.get("out_trade_no"));
            result.setTransactionId(params.get("trade_no"));
            return result;
        } catch (AlipayApiException e) {
            log.error("[alipay] 回调验签异常", e);
            return null;
        }
    }
}
