package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostCommentDeleteResp {

    // 帖子ID
    private Long postId;

    // 本次指定删除的评论ID
    private Long commentId;

    // 是否已处于删除状态
    private boolean deleted;

    // 本次实际删除的评论数量，包含一起删除的回复
    private long deletedCount;

    // 删除后帖子的评论和回复总数
    private long commentCount;
}