package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BarSiteAccountRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotBlank(message = "责任人不能为空")
    private String owner;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    private String email;

    private String status;
}
