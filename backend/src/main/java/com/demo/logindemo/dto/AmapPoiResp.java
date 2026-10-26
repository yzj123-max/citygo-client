package com.demo.logindemo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AmapPoiResp {

    // 1成功，0失败
    private String status;

    private String info;

    private String infocode;

    @JsonDeserialize(using = FlexibleTextDeserializer.class)
    private String count;

    private List<PoiItem> pois = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoiItem {

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String id;

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String name;

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String type;

        @JsonProperty("typecode")
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String typeCode;

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String address;

        // 省份名称
        @JsonProperty("pname")
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String provinceName;

        // 城市名称
        @JsonProperty("cityname")
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String cityName;

        // 区县名称
        @JsonProperty("adname")
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String districtName;

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String adcode;

        // 高德格式：经度,纬度
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String location;

        // 保留原始节点，后续判断是否为有效对象后再解析
        private JsonNode business;

        private List<PhotoItem> photos = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BusinessInfo {

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String tel;

        @JsonProperty("opentime_today")
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String openTimeToday;

        @JsonProperty("opentime_week")
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String openTimeWeek;

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String rating;

        // 人均消费，不作为门票价格
        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String cost;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PhotoItem {

        @JsonDeserialize(using = FlexibleTextDeserializer.class)
        private String url;
    }

    /**
     * 兼容高德部分空文本字段返回 [] 的情况。
     * 文本、数字转为字符串；数组、对象、null按无数据处理。
     */
    public static class FlexibleTextDeserializer
            extends JsonDeserializer<String> {

        @Override
        public String deserialize(
                JsonParser parser,
                DeserializationContext context
        ) throws IOException {
            JsonNode node = parser.readValueAsTree();

            if (node == null || node.isNull()) {
                return null;
            }

            if (!node.isTextual() && !node.isNumber()) {
                return null;
            }

            String value = node.asText().trim();

            return value.isEmpty() ? null : value;
        }
    }
}