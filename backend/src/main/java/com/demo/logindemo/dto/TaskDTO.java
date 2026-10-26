package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class TaskDTO {
    // 状态：PROCESSING 生成中 / SUCCESS 成功 / FAIL 失败
    private String status;
    private String result; // 最终攻略文本
    private String errorMsg; // 失败原因
}