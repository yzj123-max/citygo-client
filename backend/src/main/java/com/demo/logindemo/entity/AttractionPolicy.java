package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@TableName(value = "attraction_policy", autoResultMap = true)
public class AttractionPolicy {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 关联 attractions.id
    private Long attractionId;

    // TICKET：门票
    // OPENING_HOURS：开放时间
    // RESERVATION：预约
    // PET：宠物
    private String policyType;

    // 政策状态，具体取值由policyType决定
    // 未知统一为UNKNOWN
    private String policyValue;

    // 详细说明、限制条件和适用区域
    private String content;

    // 结构化补充，映射数据库JSON字段
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> structuredData;

    // OFFICIAL：官方
    // AMAP：高德
    // MANUAL：人工整理
    // UNKNOWN：未知
    private String sourceType;

    // 来源名称
    private String sourceName;

    // 来源页面或官方预约链接
    private String sourceUrl;

    // 人工核实时间，未核实为空
    private Date verifiedAt;

    // 生效时间，未知为空
    private Date effectiveFrom;

    // 结束时间，未知为空
    private Date effectiveUntil;

    // 建议重新核实时间
    private Date reviewDueAt;

    // ACTIVE：正常；HIDDEN：隐藏
    private String status;

    private Date createTime;

    private Date updateTime;
}