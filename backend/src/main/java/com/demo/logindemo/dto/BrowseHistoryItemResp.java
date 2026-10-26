package com.demo.logindemo.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class BrowseHistoryItemResp
        extends AttractionCardResp {

    /**
     * 浏览记录ID，删除单条记录时使用。
     */
    private Long historyId;

    /**
     * 当前为ATTRACTION：景点。
     */
    private String targetType;

    /**
     * 被浏览景点的ID。
     */
    private Long targetId;

    /**
     * 累计浏览次数。
     */
    private Long viewCount;

    /**
     * 最近浏览时间。
     */
    private Date lastViewedAt;

    /**
     * 景点当前是否可以正常查看。
     */
    private boolean available;
}