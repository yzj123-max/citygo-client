package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostAudience;
import com.demo.logindemo.entity.UserFollow;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.PostAudienceMapper;
import com.demo.logindemo.mapper.UserFollowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostAccessService {

    private final OotdMapper postMapper;
    private final PostAudienceMapper audienceMapper;
    private final UserFollowMapper followMapper;

    public PostAccessService(
            OotdMapper postMapper,
            PostAudienceMapper audienceMapper,
            UserFollowMapper followMapper
    ) {
        this.postMapper = postMapper;
        this.audienceMapper = audienceMapper;
        this.followMapper = followMapper;
    }

    // 判断当前用户能否查看帖子。
    public boolean canView(Ootd post, Long viewerId) {

        if (post == null
                || post.getId() == null
                || post.getUserId() == null
                || viewerId == null
                || viewerId <= 0) {
            return false;
        }

        // 已删除或非已发布状态，不允许查看。
        if (!"PUBLISHED".equals(post.getStatus())) {
            return false;
        }

        // 作者始终可以查看自己已发布的帖子。
        if (viewerId.equals(post.getUserId())) {
            return true;
        }

        String visibility = post.getVisibility();

        // 缺少观看设置时，不向其他用户开放。
        if (visibility == null) {
            return false;
        }

        return switch (visibility) {
            case "PUBLIC" -> true;

            case "PRIVATE" -> false;

            case "ALLOW_LIST" ->
                    isInAudience(post.getId(), viewerId);

            case "DENY_LIST" ->
                    isOutsideAudience(post.getId(), viewerId);

            case "FRIENDS" ->
                    isFollowing(post.getUserId(), viewerId)
                            && isFollowing(viewerId, post.getUserId());

            default -> false;
        };
    }

    // 获取当前用户有权查看的帖子。
    public Ootd requireViewable(Long postId, Long viewerId) {

        if (viewerId == null || viewerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }

        if (postId == null || postId <= 0) {
            throw notFound();
        }

        Ootd post = postMapper.selectById(postId);

        if (!canView(post, viewerId)) {
            // 不向无权限用户透露帖子是否存在。
            throw notFound();
        }

        return post;
    }

    // 只有作者可以编辑自己的已发布帖子。
    public boolean canEdit(Ootd post, Long operatorId) {

        return post != null
                && operatorId != null
                && operatorId > 0
                && operatorId.equals(post.getUserId())
                && "PUBLISHED".equals(post.getStatus());
    }

    // 当前阶段：有权查看的登录用户可以发表评论。
    public boolean canComment(Ootd post, Long viewerId) {
        return canView(post, viewerId);
    }

    // 是否处于指定名单中。
    private boolean isInAudience(Long postId, Long userId) {

        LambdaQueryWrapper<PostAudience> query =
                new LambdaQueryWrapper<>();

        query.eq(PostAudience::getPostId, postId)
                .eq(PostAudience::getUserId, userId);

        Long count = audienceMapper.selectCount(query);

        return count != null && count > 0;
    }

    // 是否不在排除名单中。
    private boolean isOutsideAudience(Long postId, Long userId) {

        LambdaQueryWrapper<PostAudience> query =
                new LambdaQueryWrapper<>();

        query.eq(PostAudience::getPostId, postId)
                .eq(PostAudience::getUserId, userId);

        Long count = audienceMapper.selectCount(query);

        return count != null && count == 0;
    }

    // 判断一个用户是否关注另一个用户。
    private boolean isFollowing(
            Long followerId,
            Long followeeId
    ) {
        LambdaQueryWrapper<UserFollow> query =
                new LambdaQueryWrapper<>();

        query.eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId);

        Long count = followMapper.selectCount(query);

        return count != null && count > 0;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "帖子不存在或不可见"
        );
    }
}