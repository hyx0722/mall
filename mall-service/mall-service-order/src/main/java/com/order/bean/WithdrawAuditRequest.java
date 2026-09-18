package com.order.bean;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员审核商家提现申请。 */
@Data
public class WithdrawAuditRequest {

    @NotNull(message = "缺少提现申请 id")
    private Long withdrawId;

    @NotNull(message = "缺少审核结论")
    private Boolean approve;

    @Size(max = 255, message = "驳回原因过长")
    private String rejectReason;
}
