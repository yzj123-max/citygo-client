package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostCollectResp {

    // 帖子ID
    private Long postId;

    // 当前用户是否已收藏
    private boolean collected;

    // 帖子当前的收藏总数
    private Long collectCount;
}