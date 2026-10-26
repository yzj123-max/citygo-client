package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PostPublishReq {

    // 帖子标题，最多100个字符
    private String title;

    // 帖子正文，最多1000个字符
    private String content;

    // 上传接口返回的图片ID，列表顺序就是展示顺序
    private List<Long> mediaIds = new ArrayList<>();

    /*
     * 观看范围：
     * PUBLIC：公开
     * PRIVATE：仅自己
     * ALLOW_LIST：仅指定用户可见
     * DENY_LIST：指定用户不可见
     * FRIENDS：仅互相关注的好友可见
     */
    private String visibility = "PRIVATE";

    // 指定可见或不可见的用户ID列表
    private List<Long> audienceUserIds = new ArrayList<>();
}