package com.payment.service;

import com.payment.bean.CreatePayOrderRequest;
import com.payment.bean.CreatePayOrderVO;

public interface PayOrderService {

    /** 创建支付单（登录态）：校验订单归属/待付款 -> 幂等建单 -> 渠道下单 */
    CreatePayOrderVO createPayOrder(CreatePayOrderRequest request);

    /** 支付成功落库（回调/模拟钩子共用）：幂等置支付成功 + 落回调记录 + 事务提交后发 pay.success */
    boolean settleSuccess(String payNo, String transactionId, String notifyType, String rawNotify);

    /** 模拟支付成功（测试钩子）：校验开关 + 归属 */
    void mockPaySuccess(Long userId, String payNo);

    /** 订单取消回执：关闭该订单仍待支付的支付单 */
    void closeUnpaidByOrderId(Long orderId);
}
