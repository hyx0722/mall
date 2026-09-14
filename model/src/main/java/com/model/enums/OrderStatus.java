package com.model.enums;

/**
 * 订单状态（orders.order_status）与状态机约束的唯一权威定义。
 *
 * 正常正向流转：
 *   WAIT_PAY --支付成功--> WAIT_SHIP --卖家全部发货--> WAIT_RECEIVE --买家确认收货--> COMPLETED
 *   WAIT_PAY --超时/买家取消/商家取消/库存扣减失败--> CANCELLED
 *
 * 退款（逆向）流转：
 *   WAIT_SHIP / WAIT_RECEIVE / COMPLETED --买家申请--> REFUNDING
 *   REFUNDING --审核驳回--> 回到申请前的状态（由 shipping_status 反推，见 OrderMapper.revertRefunding）
 *   REFUNDING --审核通过且渠道打款成功--> REFUNDED
 *
 * 状态字面量此前散落在 order 模块的 SQL 与 Java 判断里（仅靠注释维护），
 * 此处收敛后，Java 侧一律用本枚举比较；MyBatis 注解里的 SQL 字面量仍为数字，
 * 但每条语句上方都注明对应的枚举常量。
 */
public enum OrderStatus {

    /** 0-待付款：下单成功、库存已锁定，等待支付 */
    WAIT_PAY(0, "待付款"),
    /** 1-待发货：支付成功，等待卖家发货 */
    WAIT_SHIP(1, "待发货"),
    /** 2-待收货：全部卖家已发货 */
    WAIT_RECEIVE(2, "待收货"),
    /** 3-已完成：买家确认收货 */
    COMPLETED(3, "已完成"),
    /** 4-已取消：超时未付款 / 买家取消 / 商家取消 / 库存扣减失败 */
    CANCELLED(4, "已取消"),
    /** 5-退款中：买家已申请退款，等待卖家或管理员审核（审核通过后进入打款） */
    REFUNDING(5, "退款中"),
    /** 6-已退款：审核通过且渠道退款成功，库存已回补 */
    REFUNDED(6, "已退款");

    private final int code;
    private final String label;

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 按 code 解析；null/未知值返回 null（不抛异常，便于处理历史脏数据） */
    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }

    /** 当前状态是否等于给定状态（null 安全：订单状态为 null 时一律 false） */
    public static boolean is(Integer currentCode, OrderStatus expected) {
        return expected != null && expected.code == (currentCode == null ? Integer.MIN_VALUE : currentCode);
    }

    /**
     * 买家可申请退款的状态集合：待发货（未发货）/ 待收货（在途）/ 已完成（已收货退货）。
     * 待付款、已取消、退款中、已退款均不可重复申请。
     */
    public boolean refundable() {
        return this == WAIT_SHIP || this == WAIT_RECEIVE || this == COMPLETED;
    }
}
