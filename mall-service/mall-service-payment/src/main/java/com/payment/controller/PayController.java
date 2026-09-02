package com.payment.controller;

import com.model.bean.Result;
import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;
import com.payment.bean.CreatePayOrderRequest;
import com.payment.bean.CreatePayOrderVO;
import com.payment.bean.MockSuccessRequest;
import com.payment.channel.AlipayChannel;
import com.payment.channel.NotifyResult;
import com.payment.channel.WxChannel;
import com.payment.service.PayOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付接口。
 * 经网关 /pay/** StripPrefix 后：
 *  - /create、/mock/success 需登录（网关注入身份头，支付宝/微信回调路径除外）；
 *  - /alipay/notify、/wx/notify 为第三方异步回调，网关白名单放行，无登录态。
 */
@RestController
@Validated
@Slf4j
public class PayController {

    @Autowired
    PayOrderService payOrderService;
    @Autowired
    AlipayChannel alipayChannel;
    @Autowired
    WxChannel wxChannel;

    /** 创建支付单（买家，登录态） */
    @PostMapping("/create")
    public Result<CreatePayOrderVO> create(@RequestBody @Validated CreatePayOrderRequest request) {
        return Result.success(payOrderService.createPayOrder(request));
    }

    /** 模拟支付成功（仅测试钩子，需登录且支付单归属当前用户） */
    @PostMapping("/mock/success")
    public Result mockSuccess(@RequestBody @Validated MockSuccessRequest request) {
        Map<String, Object> identity = ThreadLocalUtil.get();
        if (identity == null || identity.get("id") == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = (Long) identity.get("id");
        payOrderService.mockPaySuccess(userId, request.getPayNo());
        return Result.success();
    }

    /** 支付宝异步通知：验签通过且交易成功才推进支付，返回纯文本 success/failure */
    @PostMapping(value = "/alipay/notify", produces = "text/plain;charset=UTF-8")
    public String alipayNotify(HttpServletRequest httpRequest) {
        Map<String, String> params = flattenParams(httpRequest);
        log.info("[alipay] 收到异步通知 {}", params.get("out_trade_no"));
        try {
            NotifyResult notify = alipayChannel.handleNotify(params);
            if (notify == null) {
                return "failure";
            }
            boolean ok = payOrderService.settleSuccess(notify.getPayNo(), notify.getTransactionId(),
                    "ALIPAY_NOTIFY", params.toString());
            return ok ? "success" : "failure";
        } catch (Exception e) {
            log.error("[alipay] 异步通知处理异常", e);
            return "failure";
        }
    }

    /** 微信支付异步通知：验签 + 解密后推进支付，返回 {code:SUCCESS}/{code:FAIL} */
    @PostMapping("/wx/notify")
    public Map<String, String> wxNotify(HttpServletRequest httpRequest) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Wechatpay-Serial", httpRequest.getHeader("Wechatpay-Serial"));
        headers.put("Wechatpay-Timestamp", httpRequest.getHeader("Wechatpay-Timestamp"));
        headers.put("Wechatpay-Nonce", httpRequest.getHeader("Wechatpay-Nonce"));
        headers.put("Wechatpay-Signature", httpRequest.getHeader("Wechatpay-Signature"));
        String body = readBody(httpRequest);
        log.info("[wxpay] 收到异步通知 serial={}", headers.get("Wechatpay-Serial"));
        try {
            NotifyResult notify = wxChannel.handleNotify(headers, body);
            if (notify == null) {
                return wxResult("FAIL");
            }
            boolean ok = payOrderService.settleSuccess(notify.getPayNo(), notify.getTransactionId(),
                    "WX_NOTIFY", body);
            return ok ? wxResult("SUCCESS") : wxResult("FAIL");
        } catch (Exception e) {
            log.error("[wxpay] 异步通知处理异常", e);
            return wxResult("FAIL");
        }
    }

    private Map<String, String> flattenParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v != null && v.length > 0 ? v[0] : null));
        return params;
    }

    private String readBody(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            log.error("读取回调报文失败", e);
        }
        return sb.toString();
    }

    private Map<String, String> wxResult(String code) {
        Map<String, String> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("message", "SUCCESS".equals(code) ? "成功" : "失败");
        return resp;
    }
}
