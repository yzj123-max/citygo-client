package com.demo.logindemo.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CodeUtil {
    // 内存存储：key=手机号，value=验证码
    public static final Map<String, String> codeMap = new HashMap<>();

    // 生成6位数字验证码
    public static String generateCode() {
        Random random = new Random();
        int num = random.nextInt(1000000);
        return String.format("%06d", num);
    }
}