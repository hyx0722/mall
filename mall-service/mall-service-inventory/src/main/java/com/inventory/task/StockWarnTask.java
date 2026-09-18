package com.inventory.task;

import com.inventory.mapper.InventoryFeignMapper;
import com.model.bean.Inventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存预警扫描：可用库存跌到商家设定的阈值时告警。
 *
 * 数据早就全在（inventory + inventory_log），缺的只是「阈值」和「什么时候该喊」。
 *
 * **去重**是这里的核心：任务每小时跑一次，若每次都喊，同一批缺货商品会持续刷屏直到商家补货，
 * 告警就失去意义。故用 last_warn_time 做冷却窗口（默认 24 小时）。
 *
 * 告警落地目前只有日志 + 库里的 last_warn_time。等通知中心（阶段 5）落地后，
 * 这里应改为发一条事件由它投递，届时本类只剩扫描与冷却逻辑。
 *
 * inventory 启动类已有 @EnableScheduling（与 payment 一样直接开在启动类上）。
 */
@Component
@Slf4j
public class StockWarnTask {

    @Autowired
    InventoryFeignMapper inventoryFeignMapper;

    /** 冷却窗口（小时）：同一商品在窗口内只告警一次 */
    @Value("${inventory.warn-cooldown-hours:24}")
    private int cooldownHours;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void scan() {
        try {
            List<Inventory> low = inventoryFeignMapper.selectBelowThreshold(cooldownHours);
            if (low.isEmpty()) {
                return;
            }
            for (Inventory inv : low) {
                log.warn("[inventory] 库存预警：商品 {}「{}」（卖家 {}）可用库存 {} 已跌至阈值 {}",
                        inv.getProductId(), inv.getProductName(), inv.getSellerName(),
                        inv.getAvailableStock(), inv.getWarnThreshold());
                // 标记后才开冷却窗口；标记失败不影响其它商品，下轮还会再扫到它
                inventoryFeignMapper.markWarned(inv.getId());
            }
            log.warn("[inventory] 本轮共 {} 个商品触发库存预警", low.size());
        } catch (Exception e) {
            log.error("[inventory] 库存预警扫描异常", e);
        }
    }
}
