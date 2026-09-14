package com.model.enums;

/**
 * 退款申请单的审核状态（order_refund.refund_status）。
 *
 * 注意与 payment 库 refund.refund_status（0退款中 / 1退款成功 / 2退款失败）区分：
 * 本枚举描述的是「审核与到账的业务进度」，属于 order 侧的退款申请单；
 * payment 的 refund_status 只描述「这笔钱打出去了没有」，是纯资金视角的执行结果。
 * 两者通过 refund_no 关联，不共用枚举。
 */
public enum RefundAuditStatus {

    /** 0-待审核：买家已申请，等待卖家或管理员处理 */
    PENDING(0, "待审核"),
    /** 1-审核通过：等待 payment 调渠道打款（打款结果由 pay.refund.success 事件回来） */
    APPROVED(1, "退款中"),
    /** 2-已退款：payment 打款成功，订单已置 6已退款、库存已回补 */
    REFUNDED(2, "已退款"),
    /** 3-已驳回：订单已回退到申请前的状态 */
    REJECTED(3, "已驳回");

    private final int code;
    private final String label;

    RefundAuditStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static RefundAuditStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (RefundAuditStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
