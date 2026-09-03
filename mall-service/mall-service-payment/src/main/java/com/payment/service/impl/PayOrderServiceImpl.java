package com.payment.service.impl;

import com.model.bean.Order;
import com.model.bean.Result;
import com.model.event.PaySuccessEvent;
import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;
import com.payment.bean.CreatePayOrderRequest;
import com.payment.bean.CreatePayOrderVO;
import com.payment.channel.PayChannel;
import com.payment.channel.PayParams;
import com.payment.config.PaymentRabbitConfig;
import com.payment.config.PaymentSdkProperties;
import com.payment.entity.PayOrder;
import com.payment.entity.PaymentRecord;
import com.payment.feign.OrderFeignClient;
import com.payment.mapper.PayOrderMapper;
import com.payment.mapper.PaymentRecordMapper;
import com.payment.service.OutboxService;
import com.payment.service.PayOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    PaymentRecordMapper paymentRecordMapper;
    @Autowired
    OrderFeignClient orderFeignClient;
    @Autowired
    OutboxService outboxService;
    @Autowired
    List<PayChannel> channels;
    @Autowired
    PaymentSdkProperties properties;

    @Override
    public CreatePayOrderVO createPayOrder(CreatePayOrderRequest request) {
        Map<String, Object> identity = ThreadLocalUtil.get();
        if (identity == null || identity.get("id") == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = (Long) identity.get("id");
        Integer method = request.getPaymentMethod();
        PayChannel channel = findChannel(method);

        // 拉取订单（order 服务端校验归属：id AND user_id），取金额与状态
        Result<Order> orderResult = orderFeignClient.findDetailOrder(request.getOrderId());
        if (orderResult == null || orderResult.getCode() != 0 || orderResult.getData() == null) {
            throw new BusinessException(orderResult != null ? orderResult.getMessage() : "订单服务暂不可用");
        }
        Order order = orderResult.getData();
        if (order.getOrderStatus() == null || order.getOrderStatus() != 0) {
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
            payOrder.setPayAmount(order.getTotalAmount());
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

    @Override
    @Transactional
    public boolean settleSuccess(String payNo, String transactionId, String notifyType, String rawNotify) {
        PayOrder payOrder = payOrderMapper.selectByPayNo(payNo);
        if (payOrder == null) {
            log.warn("[pay] 支付单不存在，忽略回调 payNo={}", payNo);
            return false;
        }
        // 幂等：已支付成功（重复回调）直接视为成功，不再发事件
        if (payOrder.getPaymentStatus() != null && payOrder.getPaymentStatus() == 1) {
            return true;
        }
        // 条件更新 0->1（防并发重复推进），仅受影响的第一次真正落库并发事件
        int affected = payOrderMapper.markPaid(payNo, transactionId);
        if (affected == 0) {
            return true;
        }

        PaymentRecord record = new PaymentRecord();
        record.setPayOrderId(payOrder.getId());
        record.setPayNo(payNo);
        record.setTransactionId(transactionId);
        record.setNotifyType(notifyType);
        record.setNotifyContent(rawNotify);
        record.setHandleStatus(1);
        paymentRecordMapper.insertRecord(record);

        final PaySuccessEvent event = new PaySuccessEvent();
        event.setPayNo(payNo);
        event.setOrderId(payOrder.getOrderId());
        event.setUserId(payOrder.getUserId());
        event.setTransactionId(transactionId);
        event.setPaymentMethod(payOrder.getPaymentMethod());
        // 与支付落库同事务写 outbox，由 relay 提交后可靠投递（下游订单翻转只见已落库的支付成功）
        outboxService.enqueue(PaymentRabbitConfig.ORDER_EXCHANGE, PaymentRabbitConfig.RK_PAY_SUCCESS, null, event);
        log.info("[pay] 支付成功已落库并登记 pay.success payNo={} orderId={}", payNo, event.getOrderId());
        return true;
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
        boolean ok = settleSuccess(payNo, "MOCK" + System.currentTimeMillis(), "MOCK_NOTIFY",
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
