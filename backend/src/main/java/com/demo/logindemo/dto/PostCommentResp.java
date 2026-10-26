package com.demo.logindemo.dto;

import lombok.Data;

import java.util.Date;

@Data
public class PostCommentResp {

    // 评论ID
    private Long commentId;

    // 所属帖子ID
    private Long postId;

    // 评论作者信息
    private Long authorId;

    private String authorName;

    private String authorAvatarUrl;

    // 所属一级评论ID，一级评论时为空
    private Long rootId;

    // 被回复的评论ID，一级评论时为空
    private Long replyToCommentId;

    // 被回复的用户信息，一级评论时为空
    private Long replyToUserId;

    private String replyToUserName;

    // 评论内容
    private String content;

    // NORMAL：正常；DELETED：已删除
    private String status;

    // 当前用户是否可以删除这条评论
    private boolean canDelete;

    private Date createTime;

    private Date updateTime;
}