package com.demo.logindemo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class TravelDiaryItemResp {

    /**
     * 日记ID。
     */
    private Long diaryId;

    /**
     * 日记标题。
     */
    private String title;

    /**
     * 正文摘要，完整正文通过详情接口获取。
     */
    private String contentPreview;

    /**
     * 旅行城市或目的地。
     */
    private String cityName;

    /**
     * 旅行日期。
     */
    private LocalDate travelDate;

    /**
     * 封面图片，未设置或暂不可用时为空。
     */
    private TravelDiaryImageResp coverImage;

    /**
     * 日记图片数量。
     */
    private Integer imageCount;

    /**
     * 当前数据版本。
     */
    private Long version;

    private Date createTime;

    private Date updateTime;
}
