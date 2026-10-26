package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.PostCommentPageResp;
import com.demo.logindemo.dto.PostCommentResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostComment;
import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.PostCommentMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PostCommentQueryService {

    private final PostCommentMapper commentMapper;
    private final UserMapper userMapper;
    private final PostAccessService accessService;

    public PostCommentQueryService(
            PostCommentMapper commentMapper,
            UserMapper userMapper,
            PostAccessService accessService
    ) {
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public PostCommentPageResp getComments(
            Long currentUserId,
            Long postId,
            Long rootId,
            long page,
            long pageSize
    ) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }

        // 必须先验证帖子观看权限，再查询评论。
        Ootd post = accessService.requireViewable(
                postId,
                currentUserId
        );

        if (page < 1) {
            throw new IllegalArgumentException("页码必须从1开始");
        }

        if (pageSize < 1 || pageSize > 50) {
            throw new IllegalArgumentException(
                    "每页数量必须在1到50之间"
            );
        }

        long offset;
        try {
            offset = Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("页码过大");
        }

        // 查询回复时，确认根评论属于当前帖子且仍然有效。
        if (rootId != null) {
            if (rootId <= 0) {
                throw new IllegalArgumentException(
                        "一级评论ID必须是正整数"
                );
            }

            PostComment root = commentMapper.selectById(rootId);

            if (root == null
                    || !Objects.equals(root.getPostId(), postId)
                    || root.getRootId() != null
                    || !"NORMAL".equals(root.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "所属评论不存在或已删除"
                );
            }
        }

        long total = commentMapper.selectCount(
                buildQuery(postId, rootId)
        );

        List<PostComment> comments = List.of();

        if (offset < total) {
            LambdaQueryWrapper<PostComment> query =
                    buildQuery(postId, rootId);

            query.orderByAsc(PostComment::getCreateTime)
                    .orderByAsc(PostComment::getId)
                    .last(
                            "LIMIT " + pageSize
                                    + " OFFSET " + offset
                    );

            comments = commentMapper.selectList(query);
        }

        // 批量读取用户资料，避免每条评论单独查询用户。
        Set<Long> userIds = new HashSet<>();

        for (PostComment comment : comments) {
            if (comment.getUserId() != null) {
                userIds.add(comment.getUserId());
            }

            if (comment.getReplyToUserId() != null) {
                userIds.add(comment.getReplyToUserId());
            }
        }

        Map<Long, User> users = new HashMap<>();

        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
        }

        List<PostCommentResp> records = new ArrayList<>();

        for (PostComment comment : comments) {
            User author = users.get(comment.getUserId());

            PostCommentResp item = new PostCommentResp();
            item.setCommentId(comment.getId());
            item.setPostId(comment.getPostId());
            item.setAuthorId(comment.getUserId());
            item.setAuthorName(
                    displayName(author, comment.getUserId())
            );
            item.setAuthorAvatarUrl(
                    author == null ? null : author.getAvatarUrl()
            );

            item.setRootId(comment.getRootId());
            item.setReplyToCommentId(
                    comment.getReplyToCommentId()
            );
            item.setReplyToUserId(comment.getReplyToUserId());

            if (comment.getReplyToUserId() != null) {
                item.setReplyToUserName(
                        displayName(
                                users.get(comment.getReplyToUserId()),
                                comment.getReplyToUserId()
                        )
                );
            }

            item.setContent(comment.getContent());
            item.setStatus(comment.getStatus());
            item.setCanDelete(
                    Objects.equals(
                            currentUserId,
                            comment.getUserId()
                    )
                            || Objects.equals(
                            currentUserId,
                            post.getUserId()
                    )
            );

            item.setCreateTime(comment.getCreateTime());
            item.setUpdateTime(comment.getUpdateTime());

            records.add(item);
        }

        PostCommentPageResp resp = new PostCommentPageResp();
        resp.setPostId(postId);
        resp.setRootId(rootId);
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(total);
        resp.setRecords(records);
        resp.setHasMore(
                offset < total
                        && records.size() < total - offset
        );

        return resp;
    }

    private LambdaQueryWrapper<PostComment> buildQuery(
            Long postId,
            Long rootId
    ) {
        LambdaQueryWrapper<PostComment> query =
                new LambdaQueryWrapper<>();

        query.eq(PostComment::getPostId, postId)
                .eq(PostComment::getStatus, "NORMAL");

        if (rootId == null) {
            query.isNull(PostComment::getRootId);
        } else {
            query.eq(PostComment::getRootId, rootId);
        }

        return query;
    }

    private String displayName(User user, Long userId) {
        if (user != null
                && user.getNickname() != null
                && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }

        return "用户" + userId;
    }
}