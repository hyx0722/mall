package com.user.bean;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员重置用户密码。
 */
@Data
public class AdminUserResetPwdRequest {

    @NotNull(message = "缺少用户 id")
    private Long id;

    private String newPassword;
}
