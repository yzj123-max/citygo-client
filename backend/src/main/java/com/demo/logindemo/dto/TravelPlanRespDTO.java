package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class TravelPlanRespDTO {
    // 最终生成的攻略正文
    private String planContent;
    // 来源标记：DB_ONLY / AGENT_GENERATE，用来调试
    private String sourceType;
}