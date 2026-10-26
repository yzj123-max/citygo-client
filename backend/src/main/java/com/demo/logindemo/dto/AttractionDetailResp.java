package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class AttractionDetailResp {

    private Long attractionId;

    private String name;

    private String province;

    private String city;

    private String district;

    // 景区等级
    private String level;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String coordSystem;

    private String categoryCode;

    private String categoryName;

    private String phone;

    // 简短介绍
    private String summary;

    // 详细介绍
    private String description;

    // 特色与看点
    private String highlights;

    private String suggestedDuration;

    private String bestSeason;

    private String trafficTips;

    private String visitTips;

    // 介绍资料的来源，与政策来源分开
    private String descriptionSourceName;

    private String descriptionSourceUrl;

    private Date descriptionVerifiedAt;

    // 按展示顺序排列，封面优先
    private List<ImageItem> images = new ArrayList<>();

    // 门票、开放时间、预约、宠物政策
    private List<PolicyItem> policies = new ArrayList<>();

    // 是否具有有效的景点坐标或可用入口坐标
    // 不表示手机已经获得定位权限
    private boolean canNavigate;

    // 原始采集资料独立返回，保留来源语义，不冒充已核实的当前政策。
    private CollectedInfo collectedInfo;

    @Data
    public static class CollectedInfo {
        private String ticketPrice;
        private String discountPolicy;
        private String openTime;
        private String feature;
        private String petFriendlyLevel;
        private String petFriendlinessScore;
        private String petPolicy;
        private String petPolicySource;
        private String recordedReviewTime;
    }

    @Data
    public static class ImageItem {

        private Long imageId;

        // 可直接用于展示的图片地址
        private String url;

        // TOS签名有效期，单位秒；外部图片为空
        private Long expiresInSeconds;

        private String caption;

        private boolean cover;

        private String sourceName;

        private String sourceUrl;
    }

    @Data
    public static class PolicyItem {

        // TICKET、OPENING_HOURS、RESERVATION、PET
        private String policyType;

        // 对应政策状态，缺失或不可作为当前政策使用时为UNKNOWN
        private String policyValue;

        // 政策说明
        private String content;

        private Map<String, Object> structuredData;

        private String sourceType;

        private String sourceName;

        private String sourceUrl;

        // 人工核实时间，未核实为空
        private Date verifiedAt;

        private Date effectiveFrom;

        private Date effectiveUntil;

        // CURRENT：处于有效时间范围
        // UPCOMING：尚未生效
        // EXPIRED：已经结束
        // UNSPECIFIED：未提供有效时间范围
        private String temporalStatus;

        // 是否已到建议重新核实时间
        private boolean reviewRequired;
    }
}
