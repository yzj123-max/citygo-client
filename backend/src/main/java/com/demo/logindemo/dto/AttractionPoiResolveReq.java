package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class AttractionPoiResolveReq {

    // 用户选中的高德POI ID
    // 后端会重新查询高德，核对景点资料
    private String amapPoiId;
}