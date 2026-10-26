package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostLikeResp {

    // 帖子ID
    private Long postId;

    // 当前用户是否已点赞
    private boolean liked;

    // 帖子当前的点赞总数
    private Long likeCount;
}
