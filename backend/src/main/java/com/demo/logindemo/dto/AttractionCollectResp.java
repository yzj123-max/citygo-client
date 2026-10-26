package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class AttractionCollectResp {

    /**
     * 景点ID，对应attractions.id。
     */
    private Integer attractionId;

    /**
     * 当前用户是否已收藏。
     * true：已收藏
     * false：未收藏
     */
    private boolean collected;
}