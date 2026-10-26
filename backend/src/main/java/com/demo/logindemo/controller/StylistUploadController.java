package com.demo.logindemo.controller;

import com.demo.logindemo.dto.FileUploadResp;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;

@RestController
@RequestMapping("/api/stylist/upload")
public class StylistUploadController {

    @PostMapping
    public FileUploadResp upload(@RequestParam("file") MultipartFile file) throws Exception {
        FileUploadResp resp = new FileUploadResp();
        byte[] bytes = file.getBytes();
        String base64Str = Base64.getEncoder().encodeToString(bytes);
        String prefix = "data:" + file.getContentType() + ";base64,";
        String fullBase64 = prefix + base64Str;

        resp.setImgBase64(fullBase64);
        resp.setPreviewUrl(fullBase64);
        return resp;
    }
}
