package com.demo.logindemo.dto;

import lombok.Data;
import java.util.List;

@Data
public class OotdPublishDTO {
    private String content;
    private List<String> imgUrls;
    private String styleTag;
    private String colorTag;
}