package com.model.bean;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {
    @TableField(value = "id")
    @NotNull(message = "用户ID不能为空")
    private Long id;
    @TableField(value = "username")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在2-20之间")
    private String username;
    @TableField(value = "password")
    @NotNull
    private String password;
    @TableField(value = "email")
    @Email(message = "邮件格式不对")
    private String email;
    @TableField(value = "phone")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @TableField(value = "avatar")
    private String avatar;
    @TableField(value = "status")
    private Integer status;
    @TableField(value = "role")
    private Integer role;
    @TableField(value = "created_time")
    private LocalDateTime createdTime;
    @TableField(value = "updated_time")
    private LocalDateTime updatedTime;



}
