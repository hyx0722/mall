package com.order.bean;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 退款审核请求（卖家 / 管理员共用）：对某笔退款申请通过或驳回。
 * 驳回时 rejectReason 必填（service 侧校验），通过时可空。
 */
@Data
public class RefundAuditRequest {

    @NotBlank(message = "退款单号不能为空")
    private String refundNo;

    /** true=审核通过（转打款）；false=驳回（订单回退到申请前状态） */
    @NotNull(message = "审核结论不能为空")
    private Boolean approve;

    /** 驳回原因（驳回时必填） */
    @Size(max = 255, message = "驳回原因过长")
    private String rejectReason;
}
