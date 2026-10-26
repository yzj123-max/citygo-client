package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostPublishReq;
import com.demo.logindemo.dto.PostPublishResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostAudience;
import com.demo.logindemo.entity.PostMedia;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.PostAudienceMapper;
import com.demo.logindemo.mapper.PostMediaMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PostService {
    private static final Set<String> VISIBILITIES = Set.of(
            "PUBLIC", "PRIVATE", "ALLOW_LIST", "DENY_LIST", "FRIENDS"
    );

    private final OotdMapper postMapper;
    private final PostMediaMapper mediaMapper;
    private final PostAudienceMapper audienceMapper;
    private final UserMapper userMapper;

    public PostService(
            OotdMapper postMapper,
            PostMediaMapper mediaMapper,
            PostAudienceMapper audienceMapper,
            UserMapper userMapper
    ) {
        this.postMapper = postMapper;
        this.mediaMapper = mediaMapper;
        this.audienceMapper = audienceMapper;
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostPublishResp publish(Long userId, PostPublishReq req) {
        if (userId == null || userId <= 0 || userMapper.selectById(userId) == null) {
            throw new IllegalArgumentException("登录用户不存在，请重新登录");
        }
        if (req == null) {
            throw new IllegalArgumentException("发布内容不能为空");
        }

        String title = req.getTitle() == null ? "" : req.getTitle().trim();
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (title.isEmpty() || title.length() > 100) {
            throw new IllegalArgumentException("请填写1至100个字符的标题");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("正文不能超过1000个字符");
        }

        List<Long> mediaIds = req.getMediaIds() == null
                ? new ArrayList<>() : new ArrayList<>(req.getMediaIds());
        if (mediaIds.isEmpty() || mediaIds.size() > 9) {
            throw new IllegalArgumentException("请选择1至9张已上传的图片");
        }
        Set<Long> uniqueMediaIds = new HashSet<>();
        for (int i = 0; i < mediaIds.size(); i++) {
            Long mediaId = mediaIds.get(i);
            if (mediaId == null || mediaId <= 0 || !uniqueMediaIds.add(mediaId)) {
                throw new IllegalArgumentException("图片ID无效或重复，请重新选择图片");
            }
            PostMedia media = mediaMapper.selectById(mediaId);
            if (media == null || !userId.equals(media.getUserId())
                    || !"TEMP".equals(media.getStatus()) || media.getPostId() != null) {
                throw new IllegalArgumentException(
                        "第" + (i + 1) + "张图片不可用，请选择自己上传且尚未发布的图片"
                );
            }
        }

        String visibility = req.getVisibility() == null
                ? "PRIVATE" : req.getVisibility().trim().toUpperCase(Locale.ROOT);
        if (!VISIBILITIES.contains(visibility)) {
            throw new IllegalArgumentException("观看范围无效");
        }
        boolean needsAudience = "ALLOW_LIST".equals(visibility)
                || "DENY_LIST".equals(visibility);
        Set<Long> audienceIds = new LinkedHashSet<>();
        if (req.getAudienceUserIds() != null) {
            for (Long selectedId : req.getAudienceUserIds()) {
                if (selectedId == null || selectedId <= 0) {
                    throw new IllegalArgumentException("观看名单中存在无效用户ID");
                }
                // 作者始终可见，不需要加入允许名单或排除名单。
                if (!userId.equals(selectedId)) audienceIds.add(selectedId);
            }
        }
        if (audienceIds.size() > 200) {
            throw new IllegalArgumentException("每篇帖子最多选择200名指定用户");
        }
        if (needsAudience && audienceIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一名其他用户");
        }
        if (!needsAudience && !audienceIds.isEmpty()) {
            throw new IllegalArgumentException("当前观看范围不需要指定用户名单，请清空名单");
        }
        if (needsAudience) {
            if (userMapper.selectBatchIds(audienceIds).size() != audienceIds.size()) {
                throw new IllegalArgumentException("观看名单中有不存在的用户，请重新选择");
            }
        }

        Date now = new Date();
        Ootd post = new Ootd();
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent(content);
        // 新版图片由post_media关联，不保存即将过期的签名URL。
        post.setImgUrls(new ArrayList<>());
        post.setVisibility(visibility);
        post.setStatus("PUBLISHED");
        post.setLikeCount(0L);
        post.setCollectCount(0L);
        post.setCommentCount(0L);
        post.setVersion(0L);
        post.setCreateTime(now);
        post.setUpdateTime(now);
        if (postMapper.insert(post) != 1 || post.getId() == null) {
            throw new IllegalStateException("帖子保存失败");
        }

        for (int i = 0; i < mediaIds.size(); i++) {
            LambdaUpdateWrapper<PostMedia> update = new LambdaUpdateWrapper<>();
            update.eq(PostMedia::getId, mediaIds.get(i))
                    .eq(PostMedia::getUserId, userId)
                    .eq(PostMedia::getStatus, "TEMP")
                    .isNull(PostMedia::getPostId)
                    .set(PostMedia::getPostId, post.getId())
                    .set(PostMedia::getStatus, "ATTACHED")
                    .set(PostMedia::getSortOrder, i)
                    .set(PostMedia::getUpdateTime, now);
            // 条件更新防止同一张临时图片被两个并发发布请求重复关联。
            if (mediaMapper.update(null, update) != 1) {
                throw new IllegalArgumentException("图片已被其他发布请求使用，请刷新后重试");
            }
        }

        if (needsAudience) {
            for (Long selectedId : audienceIds) {
                PostAudience audience = new PostAudience();
                audience.setPostId(post.getId());
                audience.setUserId(selectedId);
                audience.setCreateTime(now);
                if (audienceMapper.insert(audience) != 1) {
                    throw new IllegalStateException("观看名单保存失败");
                }
            }
        }

        PostPublishResp result = new PostPublishResp();
        result.setPostId(post.getId());
        result.setAuthorId(userId);
        result.setTitle(title);
        result.setContent(content);
        result.setMediaIds(mediaIds);
        result.setVisibility(visibility);
        result.setStatus("PUBLISHED");
        result.setVersion(0L);
        result.setCreateTime(now);
        return result;
    }
}
