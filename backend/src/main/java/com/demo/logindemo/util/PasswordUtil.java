package com.demo.logindemo.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtil {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // 加密明文密码（注册时用）
    public static String encode(String rawPwd){
        return encoder.encode(rawPwd);
    }

    // 比对明文和数据库密文（登录时用）
    public static boolean match(String rawPwd, String encryptedPwd){
        return encoder.matches(rawPwd, encryptedPwd);
    }
}