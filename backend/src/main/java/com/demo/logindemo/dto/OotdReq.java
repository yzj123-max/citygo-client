package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class OotdReq {
    private String base64;
    private String prompt;
}