package com.demo.logindemo.dto;

import lombok.Data;
import java.util.Date;

@Data
public class UpdateUserInfoDTO {
    private String nickname;
    private String desc;
    private String gender;
    private Date birthday;
    private String region;
    private String job;
    private String school;
}