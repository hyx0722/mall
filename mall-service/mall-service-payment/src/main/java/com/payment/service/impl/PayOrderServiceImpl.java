package com.payment.service.impl;

import com.mall.common.web.Auths;
import com.model.bean.Order;
import com.model.bean.Result;
import com.model.enums.OrderStatus;
import com.model.exception.BusinessException;
import com.payment.bean.CreatePayOrderRequest;
import com.payment.bean.CreatePayOrderVO;
import com.payment.channel.PayChannel;
import com.payment.channel.PayParams;
import com.payment.config.PaymentSdkProperties;
import com.payment.entity.PayOrder;
import com.payment.feign.OrderFeignClient;
import com.payment.mapper.PayOrderMapper;
import com.payment.service.PayOrderService;
import com.payment.service.PaySettleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class PayOrderServiceImpl implements PayOrderService {

    @Autowired
    PayOrderMapper payOrderMapper;
    @Autowired
    OrderFeignClient orderFeignClient;
    @Autowired
    PaySettleService paySettleService;
    @Autowired
    List<PayChannel> channels;
    @Autowired
    PaymentSdkProperties properties;

    @Override
    public CreatePayOrderVO createPayOrder(CreatePayOrderRequest request) {
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        Integer method = request.getPaymentMethod();
        PayChannel channel = findChannel(method);

        // 拉取订单（order 服务端校验归属：id AND user_id），取金额与状态
        Result<Order> orderResult = orderFeignClient.findDetailOrder(request.getOrderId());
        if (orderResult == null || orderResult.getCode() != 0 || orderResult.getData() == null) {
            throw new BusinessException(orderResult != null ? orderResult.getMessage() : "订单服务暂不可用");
        }
        Order order = orderResult.getData();
        // 只有待付款可支付（OrderStatus.is 为 null 安全比较，脏数据/缺字段一律判为不可支付）
        if (!OrderStatus.is(order.getOrderStatus(), OrderStatus.WAIT_PAY)) {
            throw new BusinessException("订单当前状态不可支付");
        }

        // 幂等：同一订单同渠道已存在存活待支付单则复用，避免重复建单
        PayOrder existing = payOrderMapper.selectLiveByOrderAndMethod(request.getOrderId(), method);
        PayOrder payOrder;
        if (existing != null) {
            payOrder = existing;
        } else {
            payOrder = new PayOrder();
            payOrder.setPayNo(genPayNo(userId, method));
            payOrder.setOrderId(order.getId());
            payOrder.setUserId(userId);
            payOrder.setPayAmount(payableAmount(order));
            payOrder.setPaymentMethod(method);
            payOrder.setExpireTime(LocalDateTime.now().plusMinutes(30));
            payOrderMapper.insertPayOrder(payOrder);
            log.info("[pay] 创建支付单 payNo={} orderId={} amount={}", payOrder.getPayNo(),
                    payOrder.getOrderId(), payOrder.getPayAmount());
        }

        CreatePayOrderVO vo = new CreatePayOrderVO();
        vo.setPayOrder(payOrder);
        vo.setPayParams(channel.createPay(payOrder));
        return vo;
    }

    /**
     * 实付金额 = 订单原价合计 − 优惠抵扣。
     *
     * 必须在这里扣掉 {@code discount_amount}：{@code orders.total_amount} 记的是**原价合计**，
     * 直接拿它当支付金额会让优惠券只体现在订单展示上、钱却照原价收——订单历史看着是对的，
     * 只有对账时才会发现多收了钱。
     */
    private BigDecimal payableAmount(Order order) {
        BigDecimal total = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        BigDecimal payable = total.subtract(discount);
        return payable.signum() < 0 ? BigDecimal.ZERO : payable;
    }

    @Override
    public void closeUnpaidByOrderId(Long orderId) {
        payOrderMapper.markClosedByOrderId(orderId);
    }

    @Override
    public void mockPaySuccess(Long userId, String payNo) {
        if (properties.getMock() == null || !properties.getMock().isEnabled()) {
            throw new BusinessException("模拟支付未开启（payment.mock.enabled=false）");
        }
        PayOrder payOrder = payOrderMapper.selectByPayNo(payNo);
        if (payOrder == null || !payOrder.getUserId().equals(userId)) {
            throw new BusinessException("支付单不存在或无权操作");
        }
        // 必须经由 PaySettleService 这个独立 bean 调用：同类内直接调 settleSuccess 会绕过
        // Spring 代理，@Transactional 静默失效。详见 PaySettleService 类注释。
        boolean ok = paySettleService.settleSuccess(payNo, "MOCK" + System.currentTimeMillis(), "MOCK_NOTIFY",
                "模拟支付成功（测试钩子）");
        if (!ok) {
            throw new BusinessException("支付单状态异常，模拟支付失败");
        }
    }

    private PayChannel findChannel(Integer method) {
        for (PayChannel channel : channels) {
            if (channel.method() == method) {
                return channel;
            }
        }
        throw new BusinessException("不支持的支付方式：" + method);
    }

    private String genPayNo(Long userId, Integer method) {
        return "PAY" + method
                + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", userId % 10000);
    }
}
