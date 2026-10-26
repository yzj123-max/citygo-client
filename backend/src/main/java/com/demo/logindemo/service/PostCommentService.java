package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostCommentCreateReq;
import com.demo.logindemo.dto.PostCommentResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostComment;
import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.PostCommentMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Objects;

@Service
public class PostCommentService {

    private final OotdMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final UserMapper userMapper;
    private final PostAccessService accessService;

    public PostCommentService(
            OotdMapper postMapper,
            PostCommentMapper commentMapper,
            UserMapper userMapper,
            PostAccessService accessService
    ) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public PostCommentResp create(
            Long currentUserId,
            Long postId,
            PostCommentCreateReq req
    ) {
        User author = currentUserId == null || currentUserId <= 0
                ? null
                : userMapper.selectById(currentUserId);

        if (author == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        String content = req.getContent() == null
                ? ""
                : req.getContent().trim();

        if (content.isEmpty() || content.length() > 1000) {
            throw new IllegalArgumentException(
                    "评论内容必须为1到1000个字符"
            );
        }

        if (postId == null || postId <= 0) {
            throw postNotFound();
        }

        // 锁定帖子，保证评论记录和评论总数一起更新。
        Ootd post = postMapper.selectOne(
                new LambdaQueryWrapper<Ootd>()
                        .eq(Ootd::getId, postId)
                        .last("FOR UPDATE")
        );

        if (post == null
                || !accessService.canComment(post, currentUserId)) {
            throw postNotFound();
        }

        Long rootId = null;
        Long replyToUserId = null;
        User replyToUser = null;
        Long replyToCommentId = req.getReplyToCommentId();

        if (replyToCommentId != null) {
            if (replyToCommentId <= 0) {
                throw new IllegalArgumentException(
                        "被回复的评论ID必须是正整数"
                );
            }

            PostComment target =
                    commentMapper.selectById(replyToCommentId);

            if (target == null
                    || !Objects.equals(target.getPostId(), postId)
                    || !"NORMAL".equals(target.getStatus())) {
                throw commentNotFound();
            }

            // 回复一级评论时，根评论就是目标评论；
            // 回复其他回复时，沿用它的根评论。
            rootId = target.getRootId() == null
                    ? target.getId()
                    : target.getRootId();

            PostComment root = target.getRootId() == null
                    ? target
                    : commentMapper.selectById(rootId);

            if (root == null
                    || !Objects.equals(root.getPostId(), postId)
                    || root.getRootId() != null
                    || !"NORMAL".equals(root.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "所属评论不存在或已删除，无法继续回复"
                );
            }

            replyToUserId = target.getUserId();
            replyToUser = replyToUserId == null
                    ? null
                    : userMapper.selectById(replyToUserId);
        }

        long count = post.getCommentCount() == null
                ? 0L
                : post.getCommentCount();

        if (count < 0 || count == Long.MAX_VALUE) {
            throw new IllegalStateException("帖子评论总数异常");
        }

        Date now = new Date();

        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setRootId(rootId);
        comment.setReplyToCommentId(replyToCommentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setStatus("NORMAL");
        comment.setCreateTime(now);
        comment.setUpdateTime(now);

        if (commentMapper.insert(comment) != 1
                || comment.getId() == null) {
            throw new IllegalStateException("保存评论失败");
        }

        LambdaUpdateWrapper<Ootd> update =
                new LambdaUpdateWrapper<>();

        update.eq(Ootd::getId, postId)
                .set(Ootd::getCommentCount, count + 1);

        if (postMapper.update(null, update) != 1) {
            throw new IllegalStateException("更新评论总数失败");
        }

        PostCommentResp resp = new PostCommentResp();
        resp.setCommentId(comment.getId());
        resp.setPostId(postId);
        resp.setAuthorId(currentUserId);
        resp.setAuthorName(displayName(author, currentUserId));
        resp.setAuthorAvatarUrl(author.getAvatarUrl());
        resp.setRootId(rootId);
        resp.setReplyToCommentId(replyToCommentId);
        resp.setReplyToUserId(replyToUserId);

        if (replyToUserId != null) {
            resp.setReplyToUserName(
                    displayName(replyToUser, replyToUserId)
            );
        }

        resp.setContent(content);
        resp.setStatus("NORMAL");
        resp.setCanDelete(true);
        resp.setCreateTime(now);
        resp.setUpdateTime(now);

        return resp;
    }

    private String displayName(User user, Long userId) {
        if (user != null
                && user.getNickname() != null
                && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }

        return "用户" + userId;
    }

    private ResponseStatusException postNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "帖子不存在或不可见"
        );
    }

    private ResponseStatusException commentNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "被回复的评论不存在或已删除"
        );
    }
}