package com.user.bean;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理员修改用户：必填 id，其余字段仅传需修改的项。
 */
@Data
public class AdminUserUpdateRequest {

    @NotNull(message = "缺少用户 id")
    private Long id;

    /** 状态：0-禁用 1-正常 */
    private Integer status;

    /** 角色：1-普通用户 2-管理员 */
    private Integer role;

    @Email(message = "邮件格式不对")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
