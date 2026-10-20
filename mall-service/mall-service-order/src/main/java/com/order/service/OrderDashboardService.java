package com.order.service;

import com.model.enums.OrderStatus;
import com.order.mapper.OrderDashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台看板的 order 侧聚合。
 *
 * <p>只做「取数 + 补日期 + 把状态码翻译成中文」，不在这里做跨服务调用：
 * 用户/商品/库存的统计由各自服务提供，前端并行拉取后组合。这样看板不会把
 * order 服务变成一个新的 BFF，也不会加深跨库依赖。</p>
 */
@Service
public class OrderDashboardService {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int TOP_LIMIT = 5;
    private static final int RECENT_LIMIT = 6;

    @Autowired
    OrderDashboardMapper dashboardMapper;

    public Map<String, Object> load(Integer daysParam) {
        int days = normDays(daysParam);

        Map<String, Object> summary = dashboardMapper.summary();
        List<Map<String, Object>> rawTrend = dashboardMapper.trend(days);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary == null ? Map.of() : summary);
        result.put("trend", fillMissingDays(rawTrend, days));
        result.put("statusDistribution", translateStatus(dashboardMapper.statusCounts()));
        result.put("topProducts", dashboardMapper.topProducts(days, TOP_LIMIT));
        result.put("recentOrders", dashboardMapper.recentOrders(RECENT_LIMIT));
        result.put("refund", dashboardMapper.refundSummary());
        result.put("days", days);
        return result;
    }

    private int normDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    /**
     * 数据库只会返回「有订单的日期」。折线图需要连续日期，否则没下单的那天会被跳过，
     * 视觉上把两个不相邻的日期连成一条线。这里统一补 0。
     */
    private List<Map<String, Object>> fillMissingDays(List<Map<String, Object>> rows, int days) {
        Map<String, Map<String, Object>> byDay = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            if (day != null) {
                byDay.put(String.valueOf(day), row);
            }
        }

        List<Map<String, Object>> filled = new ArrayList<>(days);
        LocalDate end = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String day = end.minusDays(i).toString();
            Map<String, Object> row = byDay.get(day);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", day);
            item.put("orderCount", row == null ? 0 : row.get("orderCount"));
            item.put("gmv", row == null ? BigDecimal.ZERO : row.get("gmv"));
            filled.add(item);
        }
        return filled;
    }

    private List<Map<String, Object>> translateStatus(List<Map<String, Object>> rows) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object codeObj = row.get("code");
            int code = codeObj instanceof Number n ? n.intValue() : -1;
            OrderStatus status = OrderStatus.of(code);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code);
            item.put("label", status == null ? "未知" : status.label());
            item.put("count", row.get("cnt"));
            items.add(item);
        }
        return items;
    }
}
