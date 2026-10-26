package com.demo.logindemo.dto;

import lombok.Data;
import java.util.Date;

@Data
public class UserInfoDTO {
    private Long id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String bgImageUrl;
    private String desc;
    private String gender;
    private Date birthday;
    private String region;
    private String job;
    private String school;
}