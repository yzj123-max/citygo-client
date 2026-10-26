package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@TableName(value = "ootd", autoResultMap = true)
public class Ootd {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 作者ID
    private Long userId;

    // 帖子标题
    private String title;

    // 帖子正文
    private String content;

    // 保留旧版图片字段，新版图片使用post_media关联
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imgUrls;

    private String styleTag;

    private String colorTag;

    // PUBLIC、PRIVATE、ALLOW_LIST、DENY_LIST、FRIENDS
    private String visibility;

    // PUBLISHED、DELETED
    private String status;

    private Long likeCount;

    private Long collectCount;

    private Long commentCount;

    // 编辑版本号
    private Long version;

    private Date createTime;

    private Date updateTime;
}