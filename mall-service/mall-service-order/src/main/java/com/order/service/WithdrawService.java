package com.order.service;

import com.model.exception.BusinessException;
import com.order.bean.Settlement;
import com.order.bean.Withdraw;
import com.order.mapper.SettlementMapper;
import com.order.mapper.WithdrawMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 商家提现与账期 T+N。
 *
 * 余额口径与状态流转见 {@link Withdraw}。
 *
 * **并发**：申请提现是典型的「读-改-写」——读可提现余额、判断够不够、再插一条占款记录。
 * 与库存扣减不同，这里**没有**一个能写进 WHERE 的条件 UPDATE 可以表达
 * 「Σ可提现 − Σ申请中 ≥ amount」，所以正确性只能靠串行化：
 * 用 per-seller 的 Redisson 锁把同一商家的申请排成队（锁名带 sellerId，不同商家互不影响）。
 * 这是本仓唯一一处「锁承担正确性而非仅性能」的地方，别照抄到库存那边去。
 */
@Service
@Slf4j
public class WithdrawService {

    private static final String LOCK_PREFIX = "lock:withdraw:";

    @Autowired
    WithdrawMapper withdrawMapper;
    @Autowired
    SettlementMapper settlementMapper;
    @Autowired
    RedissonClient redissonClient;

    /** 账期：结算明细生成后 N 天才可提现（T+N） */
    @Value("${order.settlement-delay-days:7}")
    private int settlementDelayDays;

    /** 账期扫描：把到期的待结算明细转成可提现。在 OrderScheduleConfig 的调度线程池里跑 */
    public void sweepWithdrawable() {
        int affected = settlementMapper.markWithdrawable(settlementDelayDays);
        if (affected > 0) {
            log.info("[settlement] 账期 T+{} 到点，{} 笔明细转为可提现", settlementDelayDays, affected);
        }
    }

    /** 可提现余额 = Σ可提现明细 − Σ申请中的提现（申请中先占住，防止重复申请） */
    public BigDecimal availableBalance(Long sellerId) {
        BigDecimal withdrawable =
                nvl(settlementMapper.sumNetBySellerAndStatus(sellerId, Settlement.STATUS_WITHDRAWABLE));
        BigDecimal pending = nvl(withdrawMapper.sumPendingBySeller(sellerId));
        BigDecimal balance = withdrawable.subtract(pending);
        return balance.signum() < 0 ? BigDecimal.ZERO : balance;
    }

    @Transactional
    public void apply(Long sellerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("提现金额必须大于 0");
        }
        RLock lock = redissonClient.getLock(LOCK_PREFIX + sellerId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("操作太频繁，请稍后重试");
            }
            BigDecimal balance = availableBalance(sellerId);
            if (amount.compareTo(balance) > 0) {
                throw new BusinessException("可提现余额不足，当前可提现 " + balance + " 元");
            }
            Withdraw withdraw = new Withdraw();
            withdraw.setWithdrawNo(genWithdrawNo(sellerId));
            withdraw.setSellerId(sellerId);
            withdraw.setAmount(amount);
            withdraw.setStatus(Withdraw.STATUS_PENDING);
            withdrawMapper.insertWithdraw(withdraw);
            log.info("[settlement] 商家 {} 申请提现 {} 元，单号 {}", sellerId, amount, withdraw.getWithdrawNo());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断，请重试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 管理员审核。通过即视为已打款（本仓未接真实出款通道，与支付渠道同为占位）。
     *
     * 打款成功后才把该商家**申请时刻之前**的可提现明细置为已提现——
     * 以 apply_time 为界，申请之后新结算进来的钱不会被误标。
     */
    @Transactional
    public void audit(Long withdrawId, Long auditorId, boolean approve, String rejectReason) {
        Withdraw withdraw = withdrawMapper.selectById(withdrawId);
        if (withdraw == null) {
            throw new BusinessException("提现申请不存在");
        }
        if (approve) {
            if (withdrawMapper.markPaid(withdrawId, auditorId) == 0) {
                throw new BusinessException("该申请已被处理");
            }
            settlementMapper.markWithdrawn(withdraw.getSellerId(), withdraw.getApplyTime());
        } else {
            if (rejectReason == null || rejectReason.isBlank()) {
                throw new BusinessException("驳回必须填写原因");
            }
            if (withdrawMapper.markRejected(withdrawId, auditorId, rejectReason) == 0) {
                throw new BusinessException("该申请已被处理");
            }
            // 驳回后金额自动回到可提现余额：sumPendingBySeller 不再计入它，无需任何回补动作
        }
    }

    public Map<String, Object> summary(Long sellerId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pending", nvl(settlementMapper.sumNetBySellerAndStatus(sellerId, Settlement.STATUS_PENDING)));
        map.put("withdrawable", availableBalance(sellerId));
        map.put("settlementDelayDays", settlementDelayDays);
        List<Withdraw> records = withdrawMapper.selectBySeller(sellerId);
        map.put("withdrawals", records);
        return map;
    }

    public List<Withdraw> listPending() {
        return withdrawMapper.selectByStatus(Withdraw.STATUS_PENDING);
    }

    private static String genWithdrawNo(Long sellerId) {
        return "WD" + System.currentTimeMillis()
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000))
                + String.format("%04d", sellerId % 10000);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
