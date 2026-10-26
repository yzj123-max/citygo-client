package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class UserSearchResp {

    // 用户ID
    private Long userId;

    // 昵称
    private String nickname;

    // 头像
    private String avatarUrl;

    // 当前登录用户是否关注了此用户
    private boolean following;

    // 此用户是否关注了当前登录用户
    private boolean followedBy;

    // 是否互相关注
    private boolean friend;
}