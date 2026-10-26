package com.demo.logindemo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class PostDetailResp {

    private Long postId;

    private Long authorId;

    private String authorName;

    private String authorAvatarUrl;

    private String title;

    private String content;

    // 按展示顺序返回图片
    private List<ImageItem> images = new ArrayList<>();

    private String visibility;

    private String status;

    private Long likeCount;

    private Long collectCount;

    private Long commentCount;

    // 当前登录用户是否已点赞
    private boolean liked;

    // 当前登录用户是否已收藏
    private boolean collected;

    // 当前用户是否可以编辑、评论
    private boolean canEdit;

    private boolean canComment;

    private Long version;

    private Date createTime;

    private Date updateTime;

    // 仅向作者返回观看名单，其他用户不返回此字段
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Long> audienceUserIds;

    @Data
    public static class ImageItem {

        private Long mediaId;

        // 临时签名地址，不直接暴露TOS对象名称
        private String previewUrl;

        // 签名有效期，单位：秒
        private Long previewExpiresInSeconds;

        private String contentType;

        private Integer width;

        private Integer height;

        private Integer sortOrder;
    }
}