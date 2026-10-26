package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class AttractionSearchPageResp {

    private long page;

    private long pageSize;

    // 当前页实际返回数量，不是全国搜索总数
    private int returnedCount;

    // 表示可以尝试查询下一页
    private boolean hasMore;

    private List<SearchItem> records = new ArrayList<>();

    @Data
    public static class SearchItem {

        // 已关联本地景点时返回，否则为空
        private Long attractionId;

        private String amapPoiId;

        private String name;

        private String province;

        private String city;

        private String district;

        private String address;

        private String categoryCode;

        private String categoryName;

        private BigDecimal longitude;

        private BigDecimal latitude;

        private String coordSystem;

        private String coverUrl;

        private String phone;

        // 是否已关联本地景点资料
        private boolean linked;

        // 距当前位置的直线距离，单位米
        // 普通关键词搜索不计算，返回null
        // 不代表实际步行或驾车路程
        private Long distanceMeters;
    }
}