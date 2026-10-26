package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AttractionCardResp {

    private Long attractionId;

    private String name;

    private String province;

    private String city;

    private String district;

    // 景区等级
    private String level;

    private String address;

    private String categoryCode;

    private String categoryName;

    // 简短介绍
    private String summary;

    // 封面地址，没有有效图片时为空
    private String coverUrl;

    // TOS签名有效期，单位秒；外部图片为空
    private Long coverExpiresInSeconds;

    // FREE、PAID、PARTIAL、UNKNOWN
    private String ticketStatus = "UNKNOWN";

    // ALLOWED、CONDITIONAL、NOT_ALLOWED、UNKNOWN
    private String petPolicy = "UNKNOWN";

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String coordSystem;

    // 有可用景点坐标或入口坐标
    private boolean canNavigate;

    // 距离，单位米；未提供当前位置或无法计算时为空
    // 这是直线距离，不是实际导航路程
    private Long distanceMeters;
}