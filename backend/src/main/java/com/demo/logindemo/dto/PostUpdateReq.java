package com.demo.logindemo.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateReq {

    // 帖子标题
    private String title;

    // 帖子正文
    private String content;

    // 编辑后保留的全部图片ID，排列顺序就是展示顺序
    // 可包含本帖已有图片，以及当前用户新上传的临时图片
    private List<Long> mediaIds;

    // PUBLIC：公开
    // PRIVATE：仅自己
    // ALLOW_LIST：仅选中的用户可见
    // DENY_LIST：选中的用户不可见
    // FRIENDS：互相关注的好友可见
    private String visibility;

    // ALLOW_LIST、DENY_LIST 对应的用户ID
    // 其他观看范围传空数组
    private List<Long> audienceUserIds;

    // 从帖子详情中取得的最新版本号
    // 用于防止两次编辑互相覆盖
    private Long version;
}