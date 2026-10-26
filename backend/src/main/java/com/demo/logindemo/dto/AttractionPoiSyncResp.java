package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class AttractionPoiSyncResp {

    // 本地景点ID
    private Long attractionId;

    // 本地景点名称
    private String attractionName;

    // 已关联的高德POI ID
    private String amapPoiId;

    // 本次更新的基础资料字段
    private List<String> updatedFields = new ArrayList<>();

    // 本次新增的图片数量
    private int imagesAddedCount;

    // 同步完成时间
    private Date syncedAt;
}