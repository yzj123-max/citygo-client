package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("post_collect")
public class PostCollect {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 被收藏的帖子ID
    private Long postId;

    // 收藏用户ID
    private Long userId;

    // 收藏时间
    private Date createTime;
}