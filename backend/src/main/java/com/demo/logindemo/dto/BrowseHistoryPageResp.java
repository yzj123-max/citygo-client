package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrowseHistoryPageResp {

    /**
     * 当前页码，从1开始。
     */
    private long page;

    /**
     * 每页数量。
     */
    private long pageSize;

    /**
     * 当前用户的浏览记录总数。
     * 同一景点重复浏览只算一条记录。
     */
    private long total;

    /**
     * 是否还有下一页。
     */
    private boolean hasMore;

    /**
     * 当前页的浏览记录。
     */
    private List<BrowseHistoryItemResp> records =
            new ArrayList<>();
}