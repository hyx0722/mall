package com.user.bean;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_address")
public class UserAddress {
    @TableField(value = "id")
    private Integer id;
    @TableField(value = "user_id")
    private Integer userId;
    @TableField(value = "receiver_name")
    @NotNull(message = "收货人姓名不能为空")
    private String receiverName;
    @TableField(value = "receiver_phone")
    @NotNull(message = "收货人手机号不能为空")
    private String receiverPhone;
    @TableField(value = "province")
    private String province;
    @TableField(value = "city")
    private String city;
    @TableField(value = "district")
    private String district;
    @TableField(value = "detail_address")
    @NotNull(message = "详细地址不能为空")
    private String detailAddress;
    @TableField(value = "is_default")
    private Integer isDefault;
    @TableField(value = "create_time")
    private LocalDateTime createdTime;
}
