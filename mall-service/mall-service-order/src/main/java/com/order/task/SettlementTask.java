package com.order.task;

import com.order.service.WithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 账期 T+N 扫描：把到期的待结算明细转为可提现。
 *
 * 照 {@link OrderTimeoutTask} 的写法：任务体只做 try-catch 包裹，业务在 service 里。
 * order 服务已有 {@code @EnableScheduling}（见 OrderScheduleConfig，自带线程池），无需再加。
 *
 * 频次（每小时）与「精确到点」无关：账期以天计，晚一小时不影响，扫太勤只是白跑 SQL。
 */
@Component
@Slf4j
public class SettlementTask {

    @Autowired
    WithdrawService withdrawService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void sweep() {
        try {
            withdrawService.sweepWithdrawable();
        } catch (Exception e) {
            log.error("[settlement] 账期扫描异常", e);
        }
    }
}
