package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class AttractionDetailUpdateReq {

    // 简短介绍，最多500个字符
    private String summary;

    // 详细介绍
    private String description;

    // 特色与看点
    private String highlights;

    // 建议游玩时长，最多255个字符
    private String suggestedDuration;

    // 建议游玩季节，最多255个字符
    private String bestSeason;

    // 交通出行建议
    private String trafficTips;

    // 游玩注意事项
    private String visitTips;

    // 介绍资料来源名称，最多255个字符
    private String sourceName;

    // 资料来源页面链接
    private String sourceUrl;

    // 管理员是否已核实本次提交的介绍资料
    // 为true时，后端检查来源并记录服务器核实时间
    private boolean verified;
}