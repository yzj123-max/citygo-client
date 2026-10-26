package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class FileUploadResp {
    private String imgBase64;
    private String previewUrl;
}
