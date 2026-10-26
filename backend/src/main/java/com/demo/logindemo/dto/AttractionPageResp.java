package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AttractionPageResp {

    // 当前页，从1开始
    private long page;

    // 每页数量
    private long pageSize;

    // 符合筛选条件的本地景点总数
    private long total;

    // 是否还有下一页
    private boolean hasMore;

    // 本页景点卡片
    private List<AttractionCardResp> records = new ArrayList<>();
}