package com.order.controller;

import com.mall.common.web.Auths;
import com.model.bean.Result;
import com.order.service.OrderDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台数据看板（order 侧）：概览、趋势、状态分布、热销商品、最近订单、退款待办。
 *
 * <p>经网关对外是 {@code GET /order/admin/dashboard?days=7}。仅管理员可访问。</p>
 */
@RestController
public class OrderDashboardController {

    @Autowired
    OrderDashboardService orderDashboardService;

    @GetMapping("/admin/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) Integer days) {
        // 网关只校验登录态，角色判断必须在这里做
        Auths.requireAdmin();
        return Result.success(orderDashboardService.load(days));
    }
}
