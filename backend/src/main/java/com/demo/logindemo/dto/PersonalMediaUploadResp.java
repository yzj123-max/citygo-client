package com.demo.logindemo.dto;

import lombok.Data;

import java.util.Date;

@Data
public class PersonalMediaUploadResp {

    /**
     * 图片媒体ID，保存日记时提交这个编号。
     */
    private Long mediaId;

    /**
     * 临时预览地址。
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
     * 上传成功后为TEMP。
     */
    private String status;

    /**
     * 临时图片允许绑定到日记的截止时间。
     * 与预览地址的有效期分别管理。
     */
    private Date expiresAt;
}