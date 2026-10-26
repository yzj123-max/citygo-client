package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostCommentCreateReq {

    // 评论或回复内容，最多1000个字符
    private String content;

    // 直接评论帖子时不传，或传 null
    // 回复某条评论时，填写那条评论的ID
    private Long replyToCommentId;
}