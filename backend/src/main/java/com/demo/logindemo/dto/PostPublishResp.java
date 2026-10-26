package com.demo.logindemo.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class PostPublishResp {

    // 发布成功后的帖子ID
    private Long postId;

    // 作者ID
    private Long authorId;

    private String title;

    private String content;

    // 图片ID，按展示顺序返回
    private List<Long> mediaIds;

    // 观看范围
    private String visibility;

    // 发布状态
    private String status;

    // 编辑版本号
    private Long version;

    private Date createTime;
}