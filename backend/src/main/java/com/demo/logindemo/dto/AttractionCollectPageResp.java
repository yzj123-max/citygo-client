package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AttractionCollectPageResp {

    private long page;

    private long pageSize;

    private long total;

    private boolean hasMore;

    private List<AttractionCollectItemResp> records = new ArrayList<>();
}
