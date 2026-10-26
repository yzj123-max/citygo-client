package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class UserLoginDTO {
    private String phone;
    private String password;
    // 新增验证码字段，用于验证码登录接口
    private String verifyCode;
}
