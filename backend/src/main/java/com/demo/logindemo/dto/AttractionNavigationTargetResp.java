package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class AttractionNavigationTargetResp {

    private Long attractionId;

    private String attractionName;

    // WALKING：步行；DRIVING：驾车
    private String travelMode;

    // 选中的入口ID，回退到景点坐标时为空
    private Long entranceId;

    // 实际导航目的地名称
    private String destinationName;

    // VISITOR：游客入口
    // VEHICLE：车辆入口
    // PARKING：停车场
    // ATTRACTION：景点坐标
    private String destinationType;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String coordSystem;

    // 目的地对应的高德POI标识，没有则为空
    private String amapPoiId;

    // 是否因没有适合的入口而使用景点坐标
    private boolean fallback;

    // 通行限制、停车接驳或回退提示
    private String notice;

    private String sourceName;

    private String sourceUrl;

    private Date verifiedAt;
}