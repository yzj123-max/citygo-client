package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AttractionEntranceSaveReq {

    // 入口名称，最多255个字符
    private String name;

    // VISITOR：游客入口
    // VEHICLE：车辆入口
    // PARKING：停车场
    private String entranceType;

    // 入口自身的高德POI ID，没有则为空
    private String amapPoiId;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    // 当前只接受GCJ02坐标，不自动转换其他坐标系
    private String coordSystem = "GCJ02";

    private Boolean supportsWalking;

    private Boolean supportsDriving;

    // 越大越优先，后端将限制在0到10000
    private Integer priority = 0;

    // 通行限制、停车或步行接驳提示
    private String notice;

    private String sourceName;

    private String sourceUrl;

    // 是否已经由管理员核实
    // 核实时间由服务器生成
    private boolean verified;

    // ACTIVE：正常
    // HIDDEN：隐藏，不参与导航
    // CLOSED：关闭，不参与导航
    private String status = "ACTIVE";
}