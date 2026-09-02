package com.order.service.impl;

import com.model.bean.Order;
import com.model.bean.Product;
import com.model.bean.Result;
import com.model.event.InventoryResultEvent;
import com.model.event.OrderCreatedEvent;
import com.model.util.ThreadLocalUtil;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.config.OrderRabbitConfig;
import com.order.fein.ProductFeignClient;
import com.order.mapper.OrderItemMapper;
import com.order.mapper.OrderMapper;
import com.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    OrderMapper orderMapper;
    @Autowired
    OrderItemMapper orderItemMapper;
    @Autowired
    ProductFeignClient productFeignClient;
    @Autowired
    RabbitTemplate rabbitTemplate;

    @Override
    public List<Order> findAllOrder() {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return orderMapper.findAllOrder(userId);
    }

    @Override
    public Order findDetailOrder(Integer id) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return orderMapper.findDetailOrder(id,userId);
    }

    /**
     * 下单：
     * 1) 同步 Feign 拉取商品价格/名称快照，计算总金额；
     * 2) 本地事务写 orders(order_status=0 待付款) + order_item；
     * 3) 事务提交后向 mall.order.exchange 发布 order.created。
     * 库存扣减由 inventory 消费 order.created 异步完成，成功后回执 deducted -> 订单待发货，失败回执 deduct_failed -> 取消。
     */
    @Override
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            throw new RuntimeException("未登录或缺少用户身份");
        }
        Integer userId = (Integer) map.get("id");

        String orderNo = genOrderNo(userId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        // 同步快照：拉价格/名称/主图（返回 Result<Product>）
        for (CreateOrderRequest.Item reqItem : request.getItems()) {
            Result<Product> productResult = productFeignClient.findProductById(reqItem.getProductId());
            if (productResult == null || productResult.getData() == null) {
                throw new RuntimeException("商品不存在或服务不可用: id=" + reqItem.getProductId());
            }
            Product product = productResult.getData();
            if (product.getStatus() != null && product.getStatus() == 0) {
                throw new RuntimeException("商品已下架: id=" + reqItem.getProductId());
            }
            BigDecimal qty = BigDecimal.valueOf(reqItem.getQuantity());
            BigDecimal lineTotal = product.getPrice().multiply(qty);
            totalAmount = totalAmount.add(lineTotal);

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setProductImage(product.getMainImage());
            item.setProductPrice(product.getPrice());
            item.setQuantity(reqItem.getQuantity());
            item.setTotalPrice(lineTotal);
            items.add(item);
        }

        // 订单头
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAddressId(request.getAddressId());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setOrderStatus(0); // 0-待付款
        order.setRemark(request.getRemark());
        orderMapper.insertOrder(order); // useGeneratedKeys 回填 id

        // 明细
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insertOrderItem(item);
        }

        // 事务提交后再发事件，避免下游在订单未落库时就消费
        final Integer orderId = order.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                OrderCreatedEvent event = new OrderCreatedEvent();
                event.setOrderNo(orderNo);
                event.setOrderId(orderId);
                event.setUserId(userId);
                List<OrderCreatedEvent.Item> evtItems = new ArrayList<>();
                for (CreateOrderRequest.Item reqItem : request.getItems()) {
                    OrderCreatedEvent.Item it = new OrderCreatedEvent.Item();
                    it.setProductId(reqItem.getProductId());
                    it.setQuantity(reqItem.getQuantity());
                    evtItems.add(it);
                }
                event.setItems(evtItems);
                rabbitTemplate.convertAndSend(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_ORDER_CREATED, event);
            }
        });
        return order;
    }

    /** 处理库存扣减成功回执 */
    public void handleDeducted(InventoryResultEvent event) {
        orderMapper.markDeducted(event.getOrderNo());
    }

    /** 处理库存扣减失败回执 */
    public void handleDeductFailed(InventoryResultEvent event) {
        orderMapper.markDeductFailed(event.getOrderNo());
    }

    private String genOrderNo(Integer userId) {
        return "NO" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", userId % 10000);
    }
}
