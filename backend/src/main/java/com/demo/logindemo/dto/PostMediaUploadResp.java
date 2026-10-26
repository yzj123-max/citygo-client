package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostMediaUploadResp {

    // 图片ID，发布帖子时提交这个ID
    private Long mediaId;

    // 临时签名预览地址
    private String previewUrl;

    // 预览地址有效期，单位：秒
    private Long previewExpiresInSeconds;

    // 图片类型，例如 image/jpeg
    private String contentType;

    // 图片字节数
    private Long sizeBytes;

    private Integer width;

    private Integer height;

    // TEMP表示图片已上传、尚未关联帖子
    private String status;
}
