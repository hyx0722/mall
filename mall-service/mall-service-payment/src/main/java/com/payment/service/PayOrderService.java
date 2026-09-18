package com.payment.service;

import com.payment.bean.CreatePayOrderRequest;
import com.payment.bean.CreatePayOrderVO;

public interface PayOrderService {

    /** 创建支付单（登录态）：校验订单归属/待付款 -> 幂等建单 -> 渠道下单 */
    CreatePayOrderVO createPayOrder(CreatePayOrderRequest request);

    // 注：「支付成功落库」不在此接口内——它是事务边界，实现在独立的 PaySettleService，
    //     必须经 Spring 代理调用。若放回本接口由 PayOrderServiceImpl 自调用，@Transactional 会静默失效。

    /** 模拟支付成功（测试钩子）：校验开关 + 归属 */
    void mockPaySuccess(Long userId, String payNo);

    /** 订单取消回执：关闭该订单仍待支付的支付单 */
    void closeUnpaidByOrderId(Long orderId);
}
