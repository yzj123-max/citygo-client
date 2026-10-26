package com.demo.logindemo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TravelDiaryUpdateReq {

    /**
     * 日记标题。
     */
    private String title;

    /**
     * 日记正文。
     */
    private String content;

    /**
     * 旅行城市或目的地，可清空。
     */
    private String cityName;

    /**
     * 旅行日期，格式：yyyy-MM-dd。
     */
    private LocalDate travelDate;

    /**
     * 修改时必须提交图片ID列表。
     * 保留图片：提交需要保留的媒体ID。
     * 移除全部图片：明确传空数组[]。
     */
    private List<Long> mediaIds;

    /**
     * 封面媒体ID。
     * 不填写时默认使用列表中的第一张图片。
     */
    private Long coverMediaId;

    /**
     * 查询日记时返回的数据版本。
     * 修改时必须原样传回。
     */
    private Long version;
}