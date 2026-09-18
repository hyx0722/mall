package com.payment.task;

import com.model.event.RefundRequestEvent;
import com.payment.entity.Refund;
import com.payment.mapper.RefundMapper;
import com.payment.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退款对账：把「停在退款中」的退款单重新推一遍。
 *
 * 此前这里是本仓自认的一个空洞（docs/operations.md 的已知边界）：渠道调用失败时
 * {@code executeRefund} **刻意抛异常而不是置失败**（置失败会造成「订单说退款中、退款单说失败」
 * 的永久不一致），靠消息有界重试兜瞬时故障——但重试耗尽落 q.pay.dlq 之后**没有任何机制捡回来**，
 * 订单永久卡在 5退款中，只能靠人去 DLQ 里翻。
 *
 * 本任务补上这一环：周期性捞出超过阈值仍停在退款中的单，**重投同一条 APPROVE 指令**。
 *
 * **重复打款的安全性是这件事成立的前提**：退款单号 {@code refund_no} 同时作为渠道的
 * {@code out_request_no} / {@code out_refund_no}，渠道按它幂等——重复调用不会退两次钱。
 * 少了这条性质，本任务就是危险的，而不是补偿。
 *
 * ⚠️ 注意与订单侧的对账兜底（OrderTimeoutTask）不同：那个扫的是「未付款超时」，
 * 与退款无关，二者覆盖的边界不重叠。
 */
@Component
@Slf4j
public class RefundReconcileTask {

    @Autowired
    RefundMapper refundMapper;
    @Autowired
    RefundService refundService;

    /** 停在退款中超过该分钟数即视为悬挂（要大于消费重试的最长耗时，避免打断正在重试的单） */
    @Value("${payment.refund-reconcile-minutes:10}")
    private int stuckMinutes;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void reconcile() {
        try {
            List<Refund> stuck = refundMapper.selectStuckRefunding(stuckMinutes);
            if (stuck.isEmpty()) {
                return;
            }
            log.warn("[pay] 发现 {} 笔悬挂退款单（停在退款中超过 {} 分钟），开始重投",
                    stuck.size(), stuckMinutes);
            int recovered = 0;
            for (Refund refund : stuck) {
                try {
                    refundService.handleRequest(buildApproveEvent(refund));
                    recovered++;
                } catch (Exception e) {
                    // 单笔失败不影响其它笔；它下轮还会被捞到（渠道仍幂等）
                    log.error("[pay] 退款单 {} 对账重投失败，留待下轮", refund.getRefundNo(), e);
                }
            }
            log.warn("[pay] 退款对账完成，本轮重投 {}/{} 笔", recovered, stuck.size());
        } catch (Exception e) {
            log.error("[pay] 退款对账扫描异常", e);
        }
    }

    /** 复用与消息消费完全相同的入口与分支，不另写一条打款路径 */
    private RefundRequestEvent buildApproveEvent(Refund refund) {
        RefundRequestEvent event = new RefundRequestEvent();
        event.setAction(RefundRequestEvent.ACTION_APPROVE);
        event.setRefundNo(refund.getRefundNo());
        event.setOrderId(refund.getOrderId());
        event.setUserId(refund.getUserId());
        event.setRefundAmount(refund.getRefundAmount());
        return event;
    }
}
