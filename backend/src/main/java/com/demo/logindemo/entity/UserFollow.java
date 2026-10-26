package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 发起关注的用户ID
    private Long followerId;

    // 被关注的用户ID
    private Long followeeId;

    private Date createTime;
}