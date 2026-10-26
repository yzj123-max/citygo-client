package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("attractions")
public class Attraction {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 景点名称
    private String attrName;

    // 省份
    private String province;

    // 城市
    private String city;

    // 区县名称
    private String district;

    // 景区等级，沿用原有字段
    private String level;

    // 历史门票说明
    private String ticketPrice;

    // 历史优惠政策
    private String discountPolicy;

    // 历史开放时间说明
    private String openTime;

    // 景点特色
    private String feature;

    // 历史宠物政策说明
    private String petPolicy;

    private String petFriendlyLevel;

    // 原表pet_note1为采集评分，保留原始值。
    private String petNote1;

    // 历史资料来源
    private String source;

    // 历史核实时间，原数据库为字符串
    private String verifyTime;

    // 保留原有类型，兼容现有代码
    private String recommend;

    // 历史宠物友好标记，不直接当作已核实政策
    private String petFriendly;

    @TableField("ootd_desc")
    private String ootdDesc;

    // 高德POI唯一标识
    private String amapPoiId;

    // 区县行政区编码
    private String adcode;

    // 详细地址
    private String address;

    // 经度
    private BigDecimal longitude;

    // 纬度
    private BigDecimal latitude;

    // 坐标系，高德为GCJ02
    private String coordSystem;

    // 景点分类编码
    private String categoryCode;

    // 景点分类名称
    private String categoryName;

    // 联系电话
    private String phone;

    // ACTIVE：正常
    // HIDDEN：隐藏
    // CLOSED：停用
    private String status;

    // 人工推荐权重
    private Integer sortWeight;

    // 高德基础资料同步时间
    private Date poiSyncedAt;
}
