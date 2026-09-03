package com.order.service.impl;

import com.model.bean.Order;
import com.model.bean.Product;
import com.model.bean.Result;
import com.model.event.InventoryResultEvent;
import com.model.event.OrderCanceledEvent;
import com.model.event.OrderCreatedEvent;
import com.model.event.PaySuccessEvent;
import com.model.exception.BusinessException;
import com.model.util.ThreadLocalUtil;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.bean.SellerOrderVO;
import com.order.bean.Shipping;
import com.order.config.OrderRabbitConfig;
import com.order.fein.ProductFeignClient;
import com.order.mapper.OrderItemMapper;
import com.order.mapper.OrderMapper;
import com.order.mapper.ShippingMapper;
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
    ShippingMapper shippingMapper;
    @Autowired
    ProductFeignClient productFeignClient;
    @Autowired
    RabbitTemplate rabbitTemplate;

    @Override
    public List<Order> findAllOrder() {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = (Long) map.get("id");
        return orderMapper.findAllOrder(userId);
    }

    @Override
    public Order findDetailOrder(Long id) {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = (Long) map.get("id");
        return orderMapper.findDetailOrder(id,userId);
    }

    @Override
    public List<Order> adminFindOrders(Integer status) {
        return orderMapper.findAdminOrders(status);
    }

    @Override
    public Order adminFindOrderById(Long id) {
        return orderMapper.findOrderById(id);
    }

    @Override
    public List<OrderItem> adminFindOrderItems(Long orderId) {
        return orderItemMapper.selectDetailByOrderId(orderId);
    }

    /**
     * 下单：
     * 1) 同步 Feign 拉取商品价格/名称快照，计算总金额；
     * 2) 本地事务写 orders(order_status=0 待付款) + order_item；
     * 3) 事务提交后向 mall.order.exchange 发布 order.created。
     * 库存扣减由 inventory 消费 order.created 异步完成：deducted 回执仅确认库存锁定、订单保持待付款；
     * 支付成功(pay.success)才 0->1 待发货；deduct_failed 回执 0->4 取消。
     */
    @Override
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Map<String, Object> map = ThreadLocalUtil.get();
        if (map == null || map.get("id") == null) {
            throw new BusinessException("请先登录");
        }
        Long userId = (Long) map.get("id");

        String orderNo = genOrderNo(userId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        // 同步快照：拉价格/名称/主图（返回 Result<Product>）
        for (CreateOrderRequest.Item reqItem : request.getItems()) {
            Result<Product> productResult = productFeignClient.findProductById(reqItem.getProductId());
            if (productResult == null || productResult.getData() == null) {
                throw new BusinessException("商品不存在或服务不可用: id=" + reqItem.getProductId());
            }
            Product product = productResult.getData();
            if (product.getStatus() != null && product.getStatus() == 0) {
                throw new BusinessException("商品已下架: id=" + reqItem.getProductId());
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
        final Long orderId = order.getId();
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

    /**
     * 库存扣减成功回执：库存已锁定，但订单仍处于待付款（支付成功后才待发货）。
     * 仅记日志，不改订单状态。
     */
    public void handleDeducted(InventoryResultEvent event) {
        log.info("[order] 订单 {} 库存已锁定，等待支付，订单保持待付款", event.getOrderNo());
    }

    /** 处理库存扣减失败回执：待付款 -> 已取消 */
    public void handleDeductFailed(InventoryResultEvent event) {
        orderMapper.markDeductFailed(event.getOrderNo());
    }

    /** 处理支付成功回执：待付款 -> 待发货（markPaid 带 order_status=0 条件防重），并冻结收货人快照 */
    public void handlePaid(PaySuccessEvent event) {
        orderMapper.markPaid(event.getOrderId());
        // 快照尽力而为：markPaid 成功/幂等重投都尝试补齐；订单无地址或地址已删时跳过，留给发货懒兜底
        snapshotReceiverIfAbsent(event.getOrderId());
    }

    /**
     * 支付超时自动取消：扫描超时未支付的待付款订单，条件更新 0->4；
     * 仅对真正被取消（受影响 1 行）的订单发 order.canceled，供 inventory 释放锁定、payment 关闭支付单。
     */
    @Override
    public void cancelExpiredOrders(long minutes) {
        List<Order> overdue = orderMapper.selectOverdueOrders(minutes);
        if (overdue.isEmpty()) {
            return;
        }
        for (Order order : overdue) {
            try {
                int affected = orderMapper.markCancelled(order.getOrderNo());
                if (affected == 0) {
                    continue; // 已被并发（支付/其它取消）翻转，跳过
                }
                publishCanceled(order);
                log.info("[order] 订单 {} 支付超时已取消", order.getOrderNo());
            } catch (Exception e) {
                log.error("[order] 取消订单 {} 失败", order.getOrderNo(), e);
            }
        }
    }

    @Override
    @Transactional
    public void buyerCancel(Long userId, Long orderId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 条件更新：仅本人 + 待付款，天然防并发（先被支付/取消翻转则影响 0 行）
        int affected = orderMapper.cancelUnpaidByIdAndUser(orderId, userId);
        if (affected == 0) {
            throw new BusinessException("订单不存在或当前状态不可取消");
        }
        Order order = orderMapper.findOrderById(orderId);
        publishCanceled(order);
    }

    @Override
    public List<SellerOrderVO> sellerOrders(Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        List<Order> orders = orderMapper.findSellerOrders(userId);
        List<SellerOrderVO> result = new ArrayList<>();
        for (Order order : orders) {
            SellerOrderVO vo = new SellerOrderVO();
            vo.setOrder(order);
            vo.setItems(orderItemMapper.selectMyItems(order.getId(), userId));
            // 混单（含其它卖家的商品）不可由本商家整单取消
            boolean hasForeign = orderMapper.countForeignItemLines(order.getId(), userId) > 0;
            vo.setCancellable(order.getOrderStatus() != null
                    && order.getOrderStatus() == 0 && !hasForeign);
            // 本商家的发货单（null=未发货，用于卖家端判断是否显示「发货」操作）
            vo.setShipInfo(shippingMapper.selectByOrderAndSeller(order.getId(), userId));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional
    public void sellerCancel(Long userId, Long orderId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        long mine = orderMapper.countMyItemLines(orderId, userId);
        if (mine == 0) {
            throw new BusinessException("该订单中没有你的商品");
        }
        long foreign = orderMapper.countForeignItemLines(orderId, userId);
        if (foreign > 0) {
            throw new BusinessException("订单含其他卖家的商品，暂不能取消");
        }
        int affected = orderMapper.cancelUnpaidById(orderId);
        if (affected == 0) {
            throw new BusinessException("订单不存在或当前状态不可取消");
        }
        Order order = orderMapper.findOrderById(orderId);
        publishCanceled(order);
    }

    /**
     * 商家发货（对自有商品所属订单）：
     * 事务第一条语句锁定订单行（select ... for update），串行化同一订单的并发发货——
     * 否则 RR 隔离级别下两个「最后一卖」各自读快照都会以为自己不是最后一个，订单会卡死在待发货。
     */
    @Override
    @Transactional
    public void sellerShip(Long sellerId, Long orderId, String logisticsCompany, String trackingNo, String remark) {
        if (sellerId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 1) 加锁读必须是事务第一条 DB 语句（先于任何普通 SELECT），串行化同一订单的并发发货
        Order order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 2) 归属：该订单里必须有本卖家的商品，否则越权
        if (orderMapper.countMyItemLines(orderId, sellerId) == 0) {
            throw new BusinessException("该订单中没有你的商品");
        }
        // 3) 幂等：本卖家对同一订单已有发货单（重复点击/重复提交）直接返回成功
        if (shippingMapper.selectByOrderAndSeller(orderId, sellerId) != null) {
            log.info("[order] 卖家 {} 对订单 {} 已发过货，跳过重复发货", sellerId, orderId);
            return;
        }
        // 4) 仅整单仍待发货可发货（拦截未付款/已取消/已发货待收货等）
        if (order.getOrderStatus() == null || order.getOrderStatus() != 1) {
            throw new BusinessException("订单当前状态不可发货");
        }
        // 5) 收货快照懒兜底：若支付时未冻结（如历史单/地址当时失效），发货前尽力补一次
        if (order.getReceiverName() == null) {
            snapshotReceiverIfAbsent(orderId);
        }
        // 6) 写发货单
        Shipping shipping = new Shipping();
        shipping.setShipNo(genShipNo(sellerId));
        shipping.setOrderId(orderId);
        shipping.setSellerId(sellerId);
        shipping.setLogisticsCompany(logisticsCompany);
        shipping.setTrackingNo(trackingNo);
        shipping.setRemark(remark);
        shippingMapper.insertShipping(shipping);
        log.info("[order] 卖家 {} 发货订单 {} 成功 shipNo={}", sellerId, orderId, shipping.getShipNo());
        // 7) 若这是最后一卖，整单 1待发货 -> 2待收货（条件更新防重）
        long shipped = shippingMapper.countShippedSellers(orderId);
        long total = orderMapper.countTotalSellers(orderId);
        if (shipped >= total && orderMapper.markFullyShipped(orderId) > 0) {
            log.info("[order] 订单 {} 全部卖家已发货 -> 待收货", orderId);
        }
    }

    @Override
    @Transactional
    public void buyerReceive(Long userId, Long orderId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 加锁读且限定归属（id AND user_id），并作为事务第一条 DB 语句
        Order order = orderMapper.selectOwnedForUpdate(orderId, userId);
        if (order == null || order.getOrderStatus() == null) {
            // 统一文案，避免泄露他人订单状态
            throw new BusinessException("订单不存在或当前状态不可收货");
        }
        if (order.getOrderStatus() == 3) {
            log.info("[order] 订单 {} 已确认收货，跳过重复确认", orderId);
            return; // 已完成的幂等返回
        }
        if (order.getOrderStatus() != 2) {
            throw new BusinessException("订单不存在或当前状态不可收货");
        }
        int affected = orderMapper.receiveOrder(orderId, userId);
        if (affected == 0) {
            throw new BusinessException("订单不存在或当前状态不可收货");
        }
        log.info("[order] 买家 {} 确认收货订单 {} 完成", userId, orderId);
    }

    @Override
    public List<Shipping> listShippings(Long userId, Long orderId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (orderId == null) {
            throw new BusinessException("缺少订单 id");
        }
        // 归属校验后返回物流发货单列表
        Order order = orderMapper.findDetailOrder(orderId, userId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return shippingMapper.selectByOrderId(orderId);
    }

    /** 尽力补收货人快照（receiver_name 为空才写）。无可用地址/地址已删则跳过，不抛异常。 */
    private void snapshotReceiverIfAbsent(Long orderId) {
        try {
            Order order = orderMapper.findOrderById(orderId);
            if (order == null || order.getReceiverName() != null) {
                return; // 已快照
            }
            Order addr = resolveReceiverSource(order);
            if (addr == null || addr.getReceiverName() == null) {
                log.warn("[order] 订单 {} 无可用的收货地址快照源，发货时将再尝试兜底", orderId);
                return;
            }
            orderMapper.snapshotReceiver(orderId, addr.getReceiverName(), addr.getReceiverPhone(), addr.getReceiverAddress());
            log.info("[order] 订单 {} 已冻结收货人快照 {} {}", orderId, addr.getReceiverName(), addr.getReceiverPhone());
        } catch (Exception e) {
            // 快照是尽力而为，失败不应影响支付/发货主流程
            log.warn("[order] 补收货人快照失败 orderId={}", orderId, e);
        }
    }

    /** 收货快照来源：按下单选定地址取，取不到退默认/最早地址。 */
    private Order resolveReceiverSource(Order order) {
        if (order.getAddressId() != null) {
            Order byId = orderMapper.selectAddressByIdAndUser(order.getAddressId(), order.getUserId());
            if (byId != null && byId.getReceiverName() != null) {
                return byId;
            }
        }
        return orderMapper.selectDefaultAddressByUser(order.getUserId());
    }

    private String genShipNo(Long sellerId) {
        return "SH" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", sellerId % 10000);
    }

    private void publishCanceled(Order order) {
        OrderCanceledEvent event = buildCanceledEvent(order);
        // 有事务时提交后再发，避免下游在订单取消未落库时就消费；无事务（如定时扫描）则立即发
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(OrderRabbitConfig.ORDER_EXCHANGE,
                            OrderRabbitConfig.RK_ORDER_CANCELED, event);
                }
            });
        } else {
            rabbitTemplate.convertAndSend(OrderRabbitConfig.ORDER_EXCHANGE,
                    OrderRabbitConfig.RK_ORDER_CANCELED, event);
        }
    }

    private OrderCanceledEvent buildCanceledEvent(Order order) {
        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        OrderCanceledEvent event = new OrderCanceledEvent();
        event.setOrderNo(order.getOrderNo());
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        List<OrderCanceledEvent.Item> evtItems = new ArrayList<>();
        for (OrderItem item : items) {
            OrderCanceledEvent.Item it = new OrderCanceledEvent.Item();
            it.setProductId(item.getProductId());
            it.setQuantity(item.getQuantity());
            evtItems.add(it);
        }
        event.setItems(evtItems);
        return event;
    }

    private String genOrderNo(Long userId) {
        return "NO" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", userId % 10000);
    }
}
