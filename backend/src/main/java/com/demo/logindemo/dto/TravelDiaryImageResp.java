package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class TravelDiaryImageResp {

    /**
     * 图片媒体ID。
     */
    private Long mediaId;

    /**
     * 当前有效的临时预览地址。
     */
    private String previewUrl;

    /**
     * 预览地址有效期，单位：秒。
     */
    private Long previewExpiresInSeconds;

    /**
     * 实际图片类型。
     */
    private String contentType;

    /**
     * 文件大小，单位：字节。
     */
    private Long sizeBytes;

    private Integer width;

    private Integer height;

    /**
     * 图片展示顺序，从0开始。
     */
    private Integer sortOrder;

    /**
     * 是否为日记封面。
     */
    private boolean cover;
}