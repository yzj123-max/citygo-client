package com.demo.logindemo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class TravelDiaryDetailResp {

    /**
     * 日记ID。
     */
    private Long diaryId;

    /**
     * 日记标题。
     */
    private String title;

    /**
     * 日记正文。
     */
    private String content;

    /**
     * 旅行城市或目的地。
     */
    private String cityName;

    /**
     * 旅行日期，格式：yyyy-MM-dd。
     */
    private LocalDate travelDate;

    /**
     * 封面媒体ID，没有图片时为空。
     */
    private Long coverMediaId;

    /**
     * 按展示顺序排列的图片。
     */
    private List<TravelDiaryImageResp> images =
            new ArrayList<>();

    /**
     * 当前数据版本，修改日记时需要传回。
     */
    private Long version;

    private Date createTime;

    private Date updateTime;
}