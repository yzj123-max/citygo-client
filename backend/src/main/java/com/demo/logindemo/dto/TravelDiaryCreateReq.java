package com.demo.logindemo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class TravelDiaryCreateReq {

    /**
     * 日记标题。
     */
    private String title;

    /**
     * 日记正文。
     */
    private String content;

    /**
     * 旅行城市或目的地，可不填写。
     */
    private String cityName;

    /**
     * 旅行日期，格式：yyyy-MM-dd。
     */
    private LocalDate travelDate;

    /**
     * 图片媒体ID列表，按展示顺序排列。
     * 没有图片时传空数组。
     */
    private List<Long> mediaIds = new ArrayList<>();

    /**
     * 封面媒体ID，可不填写。
     * 不填写时，后端默认使用第一张图片。
     * 填写时，必须属于mediaIds列表。
     */
    private Long coverMediaId;
}