package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class BrowseHistoryRecordReq {

    /**
     * 浏览对象类型。
     * 当前只支持ATTRACTION：景点。
     */
    private String targetType;

    /**
     * 景点ID，对应attractions.id。
     */
    private Long targetId;
}