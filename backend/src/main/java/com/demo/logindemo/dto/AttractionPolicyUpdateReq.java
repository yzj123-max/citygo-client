package com.demo.logindemo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
public class AttractionPolicyUpdateReq {

    // 政策状态，必须与请求地址中的政策类型匹配
    // 未核实时填写 UNKNOWN
    private String policyValue;

    // 详细说明，包括限制条件和适用区域
    private String content;

    // 结构化补充信息，没有则传 null
    private Map<String, Object> structuredData;

    // OFFICIAL：官方
    // AMAP：高德
    // MANUAL：人工整理
    // UNKNOWN：未知
    private String sourceType;

    private String sourceName;

    private String sourceUrl;

    // 是否已经由管理员核实
    // 核实时间由服务器记录
    private boolean verified;

    // 时间格式示例：2026-10-25T19:30:00+08:00
    // 不知道明确生效日期时传 null
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private Date effectiveFrom;

    // 不知道结束日期时传 null
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private Date effectiveUntil;

    // 建议重新核实的时间，没有则传 null
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private Date reviewDueAt;
}