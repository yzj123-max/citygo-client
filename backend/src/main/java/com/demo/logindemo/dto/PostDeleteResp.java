package com.demo.logindemo.dto;

import lombok.Data;

@Data
public class PostDeleteResp {

    // 帖子ID
    private Long postId;

    // 是否已删除
    private boolean deleted;

    // 删除后的帖子版本号
    private Long version;
}