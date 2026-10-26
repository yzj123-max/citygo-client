package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TravelDiaryPageResp {

    /**
     * 当前页码，从1开始。
     */
    private long page;

    /**
     * 每页数量。
     */
    private long pageSize;

    /**
     * 当前用户未删除的日记总数。
     */
    private long total;

    /**
     * 是否还有下一页。
     */
    private boolean hasMore;

    /**
     * 当前页的日记摘要列表。
     */
    private List<TravelDiaryItemResp> records =
            new ArrayList<>();
}
