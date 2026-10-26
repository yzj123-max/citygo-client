package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("post_comment")
public class PostComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 帖子ID
    private Long postId;

    // 评论作者ID
    private Long userId;

    // 所属一级评论ID，一级评论时为空
    private Long rootId;

    // 被回复的评论ID，一级评论时为空
    private Long replyToCommentId;

    // 被回复的用户ID，一级评论时为空
    private Long replyToUserId;

    // 评论内容
    private String content;

    // NORMAL：正常；DELETED：已删除
    private String status;

    private Date createTime;

    private Date updateTime;
}