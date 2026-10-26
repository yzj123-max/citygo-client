package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.PostDetailResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostAudience;
import com.demo.logindemo.entity.PostCollect;
import com.demo.logindemo.entity.PostLike;
import com.demo.logindemo.entity.PostMedia;
import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.PostAudienceMapper;
import com.demo.logindemo.mapper.PostCollectMapper;
import com.demo.logindemo.mapper.PostLikeMapper;
import com.demo.logindemo.mapper.PostMediaMapper;
import com.demo.logindemo.mapper.UserMapper;
import com.demo.logindemo.util.TosUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PostDetailService {

    private final PostAccessService accessService;
    private final PostMediaMapper mediaMapper;
    private final PostAudienceMapper audienceMapper;
    private final UserMapper userMapper;
    private final TosUtil tosUtil;
    private final PostLikeMapper likeMapper;
    private final PostCollectMapper collectMapper;

    @Value("${volc.tos.presignExpireSeconds:1800}")
    private long previewExpiresInSeconds;

    public PostDetailService(
            PostAccessService accessService,
            PostMediaMapper mediaMapper,
            PostAudienceMapper audienceMapper,
            UserMapper userMapper,
            TosUtil tosUtil,
            PostLikeMapper likeMapper,
            PostCollectMapper collectMapper
    ) {
        this.accessService = accessService;
        this.mediaMapper = mediaMapper;
        this.audienceMapper = audienceMapper;
        this.userMapper = userMapper;
        this.tosUtil = tosUtil;
        this.likeMapper = likeMapper;
        this.collectMapper = collectMapper;
    }

    @Transactional(readOnly = true)
    public PostDetailResp getDetail(
            Long postId,
            Long viewerId
    ) {
        // 必须先检查观看权限。
        Ootd post = accessService.requireViewable(
                postId,
                viewerId
        );

        PostDetailResp result = new PostDetailResp();

        result.setPostId(post.getId());
        result.setAuthorId(post.getUserId());
        result.setTitle(post.getTitle());
        result.setContent(post.getContent());
        result.setVisibility(post.getVisibility());
        result.setStatus(post.getStatus());
        result.setVersion(post.getVersion());
        result.setCreateTime(post.getCreateTime());
        result.setUpdateTime(post.getUpdateTime());

        result.setLikeCount(
                post.getLikeCount() == null
                        ? 0L : post.getLikeCount()
        );

        result.setCollectCount(
                post.getCollectCount() == null
                        ? 0L : post.getCollectCount()
        );

        result.setCommentCount(
                post.getCommentCount() == null
                        ? 0L : post.getCommentCount()
        );

        // 查询当前查看者是否已点赞。
        Long likedCount = likeMapper.selectCount(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, post.getId())
                        .eq(PostLike::getUserId, viewerId)
        );

        result.setLiked(
                likedCount != null && likedCount > 0
        );

        // 查询当前查看者是否已收藏。
        Long collectedCount = collectMapper.selectCount(
                new LambdaQueryWrapper<PostCollect>()
                        .eq(PostCollect::getPostId, post.getId())
                        .eq(PostCollect::getUserId, viewerId)
        );

        result.setCollected(
                collectedCount != null && collectedCount > 0
        );

        result.setCanEdit(
                accessService.canEdit(post, viewerId)
        );

        result.setCanComment(
                accessService.canComment(post, viewerId)
        );

        // 只向前端返回作者的公开资料。
        User author = userMapper.selectById(post.getUserId());

        if (author != null) {
            String nickname = author.getNickname();

            result.setAuthorName(
                    nickname == null || nickname.isBlank()
                            ? "用户" + author.getId()
                            : nickname
            );

            result.setAuthorAvatarUrl(author.getAvatarUrl());
        } else {
            result.setAuthorName("用户" + post.getUserId());
        }

        // 查询已关联到当前帖子的图片。
        LambdaQueryWrapper<PostMedia> imageQuery =
                new LambdaQueryWrapper<>();

        imageQuery.eq(PostMedia::getPostId, post.getId())
                .eq(PostMedia::getUserId, post.getUserId())
                .eq(PostMedia::getStatus, "ATTACHED")
                .orderByAsc(PostMedia::getSortOrder)
                .orderByAsc(PostMedia::getId);

        List<PostMedia> mediaList =
                mediaMapper.selectList(imageQuery);

        List<PostDetailResp.ImageItem> images =
                new ArrayList<>();

        for (PostMedia media : mediaList) {
            if (media.getObjectKey() == null
                    || media.getObjectKey().isBlank()) {
                throw new IllegalStateException(
                        "帖子图片信息不完整"
                );
            }

            String previewUrl = tosUtil.generatePresignedUrl(
                    media.getObjectKey()
            );

            if (previewUrl == null || previewUrl.isBlank()) {
                throw new IllegalStateException(
                        "图片访问地址生成失败"
                );
            }

            PostDetailResp.ImageItem image =
                    new PostDetailResp.ImageItem();

            image.setMediaId(media.getId());
            image.setPreviewUrl(previewUrl);
            image.setPreviewExpiresInSeconds(
                    previewExpiresInSeconds
            );
            image.setContentType(media.getContentType());
            image.setWidth(media.getWidth());
            image.setHeight(media.getHeight());
            image.setSortOrder(media.getSortOrder());

            images.add(image);
        }

        result.setImages(images);

        // 观看名单仅向作者返回。
        if (viewerId.equals(post.getUserId())) {
            LambdaQueryWrapper<PostAudience> audienceQuery =
                    new LambdaQueryWrapper<>();

            audienceQuery.eq(
                            PostAudience::getPostId,
                            post.getId()
                    )
                    .orderByAsc(PostAudience::getId);

            List<PostAudience> audienceList =
                    audienceMapper.selectList(audienceQuery);

            List<Long> audienceUserIds = new ArrayList<>();

            for (PostAudience audience : audienceList) {
                audienceUserIds.add(audience.getUserId());
            }

            result.setAudienceUserIds(audienceUserIds);
        }

        return result;
    }
}