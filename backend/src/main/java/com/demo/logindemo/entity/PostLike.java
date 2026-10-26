package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("post_like")
public class PostLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 被点赞的帖子ID
    private Long postId;

    // 点赞用户ID
    private Long userId;

    // 点赞时间
    private Date createTime;
}