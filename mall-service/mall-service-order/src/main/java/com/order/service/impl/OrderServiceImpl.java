package com.order.service.impl;

import com.mall.common.web.Auths;
import com.model.bean.CouponPreviewRequest;
import com.model.bean.CouponPreviewResult;
import com.model.bean.Order;
import com.model.bean.Product;
import com.model.bean.Result;
import com.model.enums.OrderStatus;
import com.model.event.InventoryResultEvent;
import com.model.event.OrderCompletedEvent;
import com.model.event.OrderCreatedEvent;
import com.model.event.OrderShippedEvent;
import com.model.event.OrderTimeoutEvent;
import com.model.event.PaySuccessEvent;
import com.model.exception.BusinessException;
import com.order.bean.CreateOrderRequest;
import com.order.bean.OrderItem;
import com.order.bean.SellerOrderVO;
import com.order.bean.Shipping;
import com.order.config.OrderRabbitConfig;
import com.order.feign.ProductFeignClient;
import com.order.feign.UserFeignClient;
import com.order.mapper.OrderItemMapper;
import com.order.mapper.OrderMapper;
import com.order.mapper.ShippingMapper;
import com.order.service.OrderCancelService;
import com.order.service.OrderService;
import com.mall.common.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    UserFeignClient userFeignClient;
    @Autowired
    OutboxService outboxService;
    @Autowired
    OrderCancelService orderCancelService;

    /** 支付超时阈值（分钟），下单时据此为延迟取消标记设置 TTL */
    @Value("${order.pay-timeout-minutes:30}")
    private long payTimeoutMinutes;

    @Override
    public List<Order> findAllOrder() {
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        return orderMapper.findAllOrder(userId);
    }

    @Override
    public Order findDetailOrder(Long id) {
        Auths.requireLogin();
        Long userId = Auths.currentUserId();
        return orderMapper.findDetailOrder(id,userId);
    }



    /**
     * 下单：
     * 1) 同步 Feign 拉取商品价格/名称快照，计算总金额；
     * 2) 本地事务写 orders(order_status=0 待付款) + order_item；
     * 3) 同事务把 order.created 与「支付超时延迟标记」写入 outbox，relay 提交后可靠投递。
     * 库存扣减由 inventory 消费 order.created 异步完成：deducted 回执仅确认库存锁定、订单保持待付款；
     * 支付成功(pay.success)才 0->1 待发货；deduct_failed 回执 0->4 取消。
     */
    @Override
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Auths.requireLogin();
        Long userId = Auths.currentUserId();

        String orderNo = genOrderNo(userId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        // 券的「指定商品/分类」范围判定要用 categoryId 与行小计，快照循环里顺手收集，
        // 避免为算券再向 product 服务回查一遍
        List<CouponPreviewRequest.Line> couponLines = new ArrayList<>();

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

            CouponPreviewRequest.Line couponLine = new CouponPreviewRequest.Line();
            couponLine.setProductId(product.getId());
            couponLine.setCategoryId(product.getCategoryId());
            couponLine.setLineTotal(lineTotal);
            couponLines.add(couponLine);
        }

        // 用券试算：规则全在 user 服务，这里只采用它给出的抵扣额
        BigDecimal discountAmount = previewCoupon(request.getUserCouponId(), couponLines);
        if (discountAmount.signum() > 0 && discountAmount.compareTo(totalAmount) >= 0) {
            // 抵扣后为 0 元：渠道不收 0 元单，且「全额白拿」几乎必然是券配错了
            throw new BusinessException("优惠券抵扣后订单金额为 0，请更换优惠券");
        }

        // 订单头
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAddressId(request.getAddressId());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setOrderStatus(OrderStatus.WAIT_PAY.code());
        order.setRemark(request.getRemark());
        orderMapper.insertOrder(order); // useGeneratedKeys 回填 id

        // 明细
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insertOrderItem(item);
        }

        // 核销券：与「折扣写进订单」同一事务——券若已被并发用掉则整体回滚，订单不会带着折扣落库
        redeemCoupon(request.getUserCouponId(), order.getId());

        // 同事务入 outbox：业务落库与事件发布原子，relay 提交后再投递（下游不会在订单未落库时消费）
        Long orderId = order.getId();
        enqueueOrderCreated(orderNo, orderId, userId, request);

        // 超时延迟标记：TTL=支付超时阈值，届时死信回主交换机触发取消（订单已支付/已取消则幂等跳过）
        OrderTimeoutEvent timeout = new OrderTimeoutEvent();
        timeout.setOrderNo(orderNo);
        outboxService.enqueue(OrderRabbitConfig.DELAY_EXCHANGE, OrderRabbitConfig.RK_DELAY_ORDER_TIMEOUT,
                payTimeoutMinutes * 60_000L, timeout);
        return order;
    }

    /**
     * 用券试算（不用券返回 0）。不可用直接抛业务异常并中止下单——文案由 user 服务统一给出
     * （门槛未满 / 不在有效期 / 不适用本单商品），order 侧不复述任何券规则。
     */
    private BigDecimal previewCoupon(Long userCouponId, List<CouponPreviewRequest.Line> lines) {
        if (userCouponId == null) {
            return BigDecimal.ZERO;
        }
        CouponPreviewRequest preview = new CouponPreviewRequest();
        preview.setUserCouponId(userCouponId);
        preview.setLines(lines);
        Result<CouponPreviewResult> result = userFeignClient.preview(preview);
        if (result == null || result.getCode() != 0 || result.getData() == null) {
            throw new BusinessException(result == null ? "优惠券服务暂不可用" : result.getMessage());
        }
        CouponPreviewResult data = result.getData();
        if (!data.isUsable()) {
            throw new BusinessException(data.getReason() == null ? "优惠券不可用" : data.getReason());
        }
        return data.getDiscountAmount() == null ? BigDecimal.ZERO : data.getDiscountAmount();
    }

    /**
     * 核销券。失败必须抛出，让下单事务整体回滚——否则订单带着折扣落库、券却没销掉，
     * 买家能拿同一张券反复抵扣。
     */
    private void redeemCoupon(Long userCouponId, Long orderId) {
        if (userCouponId == null) {
            return;
        }
        Result<Void> result = userFeignClient.use(userCouponId, orderId);
        if (result == null || result.getCode() != 0) {
            throw new BusinessException(result == null ? "优惠券服务暂不可用" : result.getMessage());
        }
    }

    private void enqueueOrderCreated(String orderNo, Long orderId, Long userId, CreateOrderRequest request) {
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
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_ORDER_CREATED, null, event);
    }

    /**
     * 库存扣减成功回执：库存已锁定，但订单仍处于待付款（支付成功后才待发货）。
     * 仅记日志，不改订单状态。
     */
    public void handleDeducted(InventoryResultEvent event) {
        log.info("[order] 订单 {} 库存已锁定，等待支付，订单保持待付款", event.getOrderNo());
    }

    /** 处理库存扣减失败回执：经统一取消漏斗 0->4 并登记 order.canceled（供 payment 关闭未付支付单） */
    public void handleDeductFailed(InventoryResultEvent event) {
        orderCancelService.cancelByOrderNo(event.getOrderNo());
    }

    /** 处理支付成功回执：待付款 -> 待发货（markPaid 带 order_status=0 条件防重），并冻结收货人快照 */
    public void handlePaid(PaySuccessEvent event) {
        orderMapper.markPaid(event.getOrderId());
        // 快照尽力而为：markPaid 成功/幂等重投都尝试补齐；订单无地址或地址已删时跳过，留给发货懒兜底
        snapshotReceiverIfAbsent(event.getOrderId());
    }

    /**
     * 支付超时自动取消的对账兜底（主路径已改为延迟消息）：低频扫描超时未支付的待付款订单，
     * 逐单走统一取消漏斗（条件更新 0->4 + 同事务 outbox 发 order.canceled）。
     */
    @Override
    public void cancelExpiredOrders(long minutes) {
        List<Order> overdue = orderMapper.selectOverdueOrders(minutes);
        if (overdue.isEmpty()) {
            return;
        }
        for (Order order : overdue) {
            try {
                orderCancelService.cancelByOrderNo(order.getOrderNo());
                log.info("[order] 对账扫描取消超时订单 {}", order.getOrderNo());
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
        // 同事务把 order.canceled 写入 outbox（释放库存/关支付单由下游消费）
        orderCancelService.enqueueCanceledForOrder(orderId);
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
            vo.setCancellable(OrderStatus.is(order.getOrderStatus(), OrderStatus.WAIT_PAY) && !hasForeign);
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
        // 同事务把 order.canceled 写入 outbox
        orderCancelService.enqueueCanceledForOrder(orderId);
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
        // 4) 仅整单仍待发货可发货（拦截未付款/已取消/已发货待收货/退款中等）
        if (!OrderStatus.is(order.getOrderStatus(), OrderStatus.WAIT_SHIP)) {
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
            // 同事务入箱 order.shipped。**必须挂在这个分支上**（即整单翻转的那一次），
            // 不能每个卖家发一次：买家侧的通知去重键是 (user_id, type, ref_id)，
            // 第二个卖家的那条会被**静默吞掉**——是丢失，不是重复，比重复难查得多。
            // 放在这里天然每张订单恰好一次：markFullyShipped 是带 order_status=1 的条件
            // UPDATE（只有第一次返回 >0），且上面第 3 步已挡掉同一卖家的重复发货。
            enqueueOrderShipped(order, logisticsCompany, trackingNo);
        }
    }

    /**
     * 入箱 order.shipped。
     *
     * ⚠️ 必须走 {@code outboxService.enqueue}：直接 {@code rabbitTemplate.send} 会绕过 outbox 行，
     * 而 {@code ReturnsCallback} 是靠消息里的 outbox 行 id 反查行号的——绕过之后消息一旦
     * 无法路由就被丢弃，且**不进任何指标**（见 OutboxConfirmInstaller 的说明）。
     *
     * 事件体里带的是**最后一个卖家**的物流信息，且两个字段都可空（ShipRequest 未加约束）。
     * 买家要看全部发货单得进订单详情。
     */
    private void enqueueOrderShipped(Order order, String logisticsCompany, String trackingNo) {
        OrderShippedEvent event = new OrderShippedEvent();
        event.setOrderNo(order.getOrderNo());
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        event.setLogisticsCompany(logisticsCompany);
        event.setTrackingNo(trackingNo);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_ORDER_SHIPPED, null, event);
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
        if (OrderStatus.is(order.getOrderStatus(), OrderStatus.COMPLETED)) {
            log.info("[order] 订单 {} 已确认收货，跳过重复确认", orderId);
            return; // 已完成的幂等返回
        }
        if (!OrderStatus.is(order.getOrderStatus(), OrderStatus.WAIT_RECEIVE)) {
            throw new BusinessException("订单不存在或当前状态不可收货");
        }
        int affected = orderMapper.receiveOrder(orderId, userId);
        if (affected == 0) {
            throw new BusinessException("订单不存在或当前状态不可收货");
        }
        // 同事务入箱 order.completed：结算（本服务消费）与评价等下游都以「订单完成」为起点。
        // 只在条件更新真的翻转了状态时才发——重复收货在上面已提前 return，不会重复发事件。
        enqueueOrderCompleted(order);
        log.info("[order] 买家 {} 确认收货订单 {} 完成", userId, orderId);
    }

    private void enqueueOrderCompleted(Order order) {
        OrderCompletedEvent event = new OrderCompletedEvent();
        event.setOrderNo(order.getOrderNo());
        event.setOrderId(order.getId());
        event.setUserId(order.getUserId());
        List<OrderCompletedEvent.Item> evtItems = new ArrayList<>();
        for (OrderItem item : orderItemMapper.selectByOrderId(order.getId())) {
            OrderCompletedEvent.Item it = new OrderCompletedEvent.Item();
            it.setProductId(item.getProductId());
            it.setQuantity(item.getQuantity());
            evtItems.add(it);
        }
        event.setItems(evtItems);
        outboxService.enqueue(OrderRabbitConfig.ORDER_EXCHANGE, OrderRabbitConfig.RK_ORDER_COMPLETED, null, event);
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

    private String genOrderNo(Long userId) {
        return "NO" + System.currentTimeMillis()
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", userId % 10000);
    }
}
