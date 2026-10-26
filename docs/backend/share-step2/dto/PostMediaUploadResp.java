package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostMediaUploadResp {
    private Long mediaId;
    private String previewUrl;
    private Long previewExpiresInSeconds;
    private String contentType;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private String status;
}
