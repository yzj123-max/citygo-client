package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("post_audience")
public class PostAudience {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 所属帖子ID
    private Long postId;

    // 被选中的用户ID
    private Long userId;

    private Date createTime;
}