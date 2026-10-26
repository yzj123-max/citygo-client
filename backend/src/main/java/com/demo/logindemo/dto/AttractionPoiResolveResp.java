package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class AttractionPoiResolveResp {

    // 本地景点ID，前端用它查询详情和导航目的地
    private Long attractionId;

    // 本地景点名称
    private String attractionName;

    // 高德POI ID
    private String amapPoiId;

    // true：本次新建了景点记录
    // false：复用了已有记录
    private boolean created;
}
