package com.order.service;

import com.model.bean.Order;
import com.model.event.OrderCompletedEvent;
import com.order.bean.Settlement;
import com.order.bean.SettlementLine;
import com.order.mapper.OrderMapper;
import com.order.mapper.SettlementMapper;
import com.order.support.DiscountAllocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家结算：消费 {@code order.completed}，把每笔订单明细记成一笔应结给卖家的款。
 *
 * 与 {@code OrderCancelService} 同样是「无接口的纯业务类」，故不额外抽接口。
 *
 * 金额口径见 {@link Settlement}。**幂等**靠 {@code settlement.uk_order_item}：
 * order.completed 可能被重投（relay 是 at-least-once），重投时插不进去，
 * 按行捕获 DuplicateKeyException 跳过即可，不靠「先查后插」。
 */
@Service
@Slf4j
public class SettlementService {

    private static final int MONEY_SCALE = 2;

    @Autowired
    SettlementMapper settlementMapper;
    @Autowired
    OrderMapper orderMapper;

    /** 平台佣金率，按**行实付**计（不按原价——否则平台会从自己让利出去的钱里再抽一份） */
    @Value("${order.commission-rate:0.05}")
    private BigDecimal commissionRate;

    @Transactional
    public void settleOrder(OrderCompletedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("[settlement] order.completed 事件缺少 orderId，跳过结算");
            return;
        }
        Long orderId = event.getOrderId();
        Order order = orderMapper.findOrderById(orderId);
        if (order == null) {
            log.warn("[settlement] 订单 {} 不存在，跳过结算", orderId);
            return;
        }
        List<SettlementLine> lines = settlementMapper.selectSettlementLines(orderId);
        if (lines.isEmpty()) {
            // 抛异常让消息重试：订单已完成却查不到明细，说明是异常状态而非「无需结算」
            throw new IllegalStateException("[settlement] 订单 " + orderId + " 无明细，无法结算");
        }

        // 整单优惠按行占比分摊 —— 与日后部分退款共用同一实现，避免两处口径漂移
        List<BigDecimal> lineTotals = new ArrayList<>(lines.size());
        for (SettlementLine line : lines) {
            lineTotals.add(nvl(line.getTotalPrice()));
        }
        List<BigDecimal> discounts =
                DiscountAllocator.allocate(order.getTotalAmount(), order.getDiscountAmount(), lineTotals);

        int written = 0;
        for (int i = 0; i < lines.size(); i++) {
            SettlementLine line = lines.get(i);
            if (line.getSellerId() == null) {
                // 商品已被物理删除：不知道这笔钱该给谁，只能跳过并告警（不能默认给平台）
                log.error("[settlement] 订单 {} 的商品 {} 已不存在，无法确定卖家，跳过该行",
                        orderId, line.getProductId());
                continue;
            }
            BigDecimal gross = nvl(line.getTotalPrice());
            BigDecimal discount = discounts.get(i);
            BigDecimal paid = gross.subtract(discount);
            BigDecimal commission = paid.multiply(commissionRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            Settlement settlement = new Settlement();
            settlement.setOrderId(orderId);
            settlement.setOrderNo(order.getOrderNo());
            settlement.setOrderItemId(line.getOrderItemId());
            settlement.setSellerId(line.getSellerId());
            settlement.setProductId(line.getProductId());
            settlement.setGrossAmount(gross);
            settlement.setDiscountAmount(discount);
            settlement.setCommissionAmount(commission);
            settlement.setNetAmount(paid.subtract(commission));
            settlement.setStatus(Settlement.STATUS_PENDING);
            try {
                settlementMapper.insertSettlement(settlement);
                written++;
            } catch (DuplicateKeyException e) {
                // 重投：该明细已记过账，跳过。MySQL 的唯一键冲突不会中止整个事务
                log.debug("[settlement] 明细 {} 已记过账，跳过", line.getOrderItemId());
            }
        }
        log.info("[settlement] 订单 {} 结算完成，写入 {} 笔明细", orderId, written);
    }

    /** 卖家对账视图：按状态汇总应结金额 + 明细列表 */
    public Map<String, Object> sellerSummary(Long sellerId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pending", nvl(settlementMapper.sumNetBySellerAndStatus(sellerId, 0)));
        summary.put("withdrawable", nvl(settlementMapper.sumNetBySellerAndStatus(sellerId, 1)));
        summary.put("withdrawn", nvl(settlementMapper.sumNetBySellerAndStatus(sellerId, 2)));
        summary.put("commissionRate", commissionRate);
        // 注：pending -> 可提现 的账期扫描（T+N）与提现申请见 WithdrawService，
        // 卖家侧的完整视图用 GET /order/seller/withdraw
        List<Settlement> items = settlementMapper.selectBySeller(sellerId);
        summary.put("items", items);
        summary.put("total", items.size());
        return summary;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
