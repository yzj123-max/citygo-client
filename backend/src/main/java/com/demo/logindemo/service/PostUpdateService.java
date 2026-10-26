package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostDetailResp;
import com.demo.logindemo.dto.PostUpdateReq;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostAudience;
import com.demo.logindemo.entity.PostMedia;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.PostAudienceMapper;
import com.demo.logindemo.mapper.PostMediaMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class PostUpdateService {

    private static final Set<String> VISIBILITIES = Set.of(
            "PUBLIC",
            "PRIVATE",
            "ALLOW_LIST",
            "DENY_LIST",
            "FRIENDS"
    );

    private final OotdMapper postMapper;
    private final PostMediaMapper mediaMapper;
    private final PostAudienceMapper audienceMapper;
    private final UserMapper userMapper;
    private final PostDetailService detailService;

    public PostUpdateService(
            OotdMapper postMapper,
            PostMediaMapper mediaMapper,
            PostAudienceMapper audienceMapper,
            UserMapper userMapper,
            PostDetailService detailService
    ) {
        this.postMapper = postMapper;
        this.mediaMapper = mediaMapper;
        this.audienceMapper = audienceMapper;
        this.userMapper = userMapper;
        this.detailService = detailService;
    }

    @Transactional(rollbackFor = Exception.class)
    public PostDetailResp update(
            Long currentUserId,
            Long postId,
            PostUpdateReq req
    ) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }

        if (postId == null || postId <= 0) {
            throw notFound();
        }

        Ootd post = postMapper.selectById(postId);

        if (post == null
                || !Objects.equals(post.getUserId(), currentUserId)
                || !"PUBLISHED".equals(post.getStatus())) {
            throw notFound();
        }

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        Long version = req.getVersion();
        if (version == null || version < 0
                || version == Long.MAX_VALUE) {
            throw new IllegalArgumentException("请携带有效的帖子版本号");
        }

        if (!Objects.equals(post.getVersion(), version)) {
            throw conflict();
        }

        String title = req.getTitle() == null
                ? ""
                : req.getTitle().trim();

        if (title.isEmpty() || title.length() > 100) {
            throw new IllegalArgumentException("标题长度必须为1到100个字符");
        }

        String content = req.getContent() == null
                ? ""
                : req.getContent().trim();

        if (content.length() > 1000) {
            throw new IllegalArgumentException("正文不能超过1000个字符");
        }

        String visibility = req.getVisibility() == null
                ? ""
                : req.getVisibility().trim().toUpperCase(Locale.ROOT);

        if (!VISIBILITIES.contains(visibility)) {
            throw new IllegalArgumentException("请选择有效的观看范围");
        }

        List<Long> mediaIds = validateMediaIds(req.getMediaIds());

        List<PostMedia> selectedMedia = new ArrayList<>();
        for (Long mediaId : mediaIds) {
            PostMedia media = mediaMapper.selectById(mediaId);

            if (media == null
                    || !Objects.equals(media.getUserId(), currentUserId)) {
                throw new IllegalArgumentException(
                        "图片不存在或不属于当前用户，图片ID：" + mediaId
                );
            }

            boolean existingImage =
                    "ATTACHED".equals(media.getStatus())
                            && Objects.equals(media.getPostId(), postId);

            boolean newImage =
                    "TEMP".equals(media.getStatus())
                            && media.getPostId() == null;

            if (!existingImage && !newImage) {
                throw new IllegalArgumentException(
                        "图片不可使用，请重新上传，图片ID：" + mediaId
                );
            }

            selectedMedia.add(media);
        }

        List<Long> audienceIds = validateAudience(
                currentUserId,
                visibility,
                req.getAudienceUserIds()
        );

        Date now = new Date();

        // 先校验并更新版本号，同时锁定当前帖子，
        // 后续任一步失败都会回滚整个编辑操作。
        LambdaUpdateWrapper<Ootd> postUpdate =
                new LambdaUpdateWrapper<>();

        postUpdate.eq(Ootd::getId, postId)
                .eq(Ootd::getUserId, currentUserId)
                .eq(Ootd::getStatus, "PUBLISHED")
                .eq(Ootd::getVersion, version)
                .set(Ootd::getTitle, title)
                .set(Ootd::getContent, content)
                .set(Ootd::getVisibility, visibility)
                .set(Ootd::getVersion, version + 1)
                .set(Ootd::getUpdateTime, now);

        if (postMapper.update(null, postUpdate) != 1) {
            throw conflict();
        }

        // 更新保留图片的顺序，并绑定新上传的图片。
        for (int i = 0; i < selectedMedia.size(); i++) {
            PostMedia media = selectedMedia.get(i);

            LambdaUpdateWrapper<PostMedia> mediaUpdate =
                    new LambdaUpdateWrapper<>();

            mediaUpdate.eq(PostMedia::getId, media.getId())
                    .eq(PostMedia::getUserId, currentUserId);

            if ("TEMP".equals(media.getStatus())) {
                mediaUpdate.eq(PostMedia::getStatus, "TEMP")
                        .isNull(PostMedia::getPostId);
            } else {
                mediaUpdate.eq(PostMedia::getStatus, "ATTACHED")
                        .eq(PostMedia::getPostId, postId);
            }

            mediaUpdate.set(PostMedia::getPostId, postId)
                    .set(PostMedia::getStatus, "ATTACHED")
                    .set(PostMedia::getSortOrder, i)
                    .set(PostMedia::getUpdateTime, now);

            if (mediaMapper.update(null, mediaUpdate) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "图片状态已变化，请刷新后重试"
                );
            }
        }

        // 从本帖移除未保留的图片。
        // 此处只修改数据库状态，不立即删除TOS对象。
        LambdaUpdateWrapper<PostMedia> removeImages =
                new LambdaUpdateWrapper<>();

        removeImages.eq(PostMedia::getPostId, postId)
                .eq(PostMedia::getUserId, currentUserId)
                .eq(PostMedia::getStatus, "ATTACHED")
                .notIn(PostMedia::getId, mediaIds)
                .set(PostMedia::getStatus, "DETACHED")
                .set(PostMedia::getUpdateTime, now);

        mediaMapper.update(null, removeImages);

        // 用新的可见用户列表替换旧列表。
        audienceMapper.delete(
                new LambdaQueryWrapper<PostAudience>()
                        .eq(PostAudience::getPostId, postId)
        );

        for (Long audienceUserId : audienceIds) {
            PostAudience audience = new PostAudience();
            audience.setPostId(postId);
            audience.setUserId(audienceUserId);
            audience.setCreateTime(now);

            if (audienceMapper.insert(audience) != 1) {
                throw new IllegalStateException("保存观看范围失败");
            }
        }

        return detailService.getDetail(postId, currentUserId);
    }

    private List<Long> validateMediaIds(List<Long> input) {
        if (input == null || input.isEmpty() || input.size() > 9) {
            throw new IllegalArgumentException("请选择1到9张图片");
        }

        List<Long> ids = new ArrayList<>(input);
        Set<Long> unique = new HashSet<>();

        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("图片ID必须是正整数");
            }

            if (!unique.add(id)) {
                throw new IllegalArgumentException("不能重复选择同一张图片");
            }
        }

        return ids;
    }

    private List<Long> validateAudience(
            Long currentUserId,
            String visibility,
            List<Long> input
    ) {
        boolean needsAudience =
                "ALLOW_LIST".equals(visibility)
                        || "DENY_LIST".equals(visibility);

        if (!needsAudience) {
            if (input != null && !input.isEmpty()) {
                throw new IllegalArgumentException(
                        "当前观看范围不需要指定用户，请传空数组"
                );
            }
            return List.of();
        }

        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一位用户");
        }

        if (input.size() > 200) {
            throw new IllegalArgumentException("最多选择200位用户");
        }

        Set<Long> unique = new LinkedHashSet<>();

        for (Long userId : input) {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("用户ID必须是正整数");
            }

            if (!Objects.equals(userId, currentUserId)) {
                unique.add(userId);
            }
        }

        if (unique.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一位其他用户");
        }

        List<Long> ids = new ArrayList<>(unique);

        if (userMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new IllegalArgumentException(
                    "选中的部分用户不存在，请重新选择"
            );
        }

        return ids;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "帖子不存在或不可编辑"
        );
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "帖子已被修改，请刷新详情后重新编辑"
        );
    }
}
