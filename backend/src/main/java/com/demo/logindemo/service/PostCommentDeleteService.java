package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostCommentDeleteResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostComment;
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
public class PostCommentDeleteService {

    private final OotdMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final UserMapper userMapper;
    private final PostAccessService accessService;

    public PostCommentDeleteService(
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
    public PostCommentDeleteResp delete(
            Long currentUserId,
            Long postId,
            Long commentId
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
            throw postNotFound();
        }

        if (commentId == null || commentId <= 0) {
            throw commentNotFound();
        }

        // 与发表评论使用相同的帖子锁，
        // 避免删除一级评论时又有新回复写入。
        Ootd post = postMapper.selectOne(
                new LambdaQueryWrapper<Ootd>()
                        .eq(Ootd::getId, postId)
                        .last("FOR UPDATE")
        );

        if (post == null
                || !accessService.canView(post, currentUserId)) {
            throw postNotFound();
        }

        PostComment comment = commentMapper.selectById(commentId);

        if (comment == null
                || !Objects.equals(comment.getPostId(), postId)) {
            throw commentNotFound();
        }

        boolean isCommentAuthor =
                Objects.equals(currentUserId, comment.getUserId());

        boolean isPostAuthor =
                Objects.equals(currentUserId, post.getUserId());

        if (!isCommentAuthor && !isPostAuthor) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只能删除自己的评论或自己帖子下的评论"
            );
        }

        long currentCount = post.getCommentCount() == null
                ? 0L
                : post.getCommentCount();

        if (currentCount < 0) {
            throw new IllegalStateException("帖子评论总数异常");
        }

        // 重复删除直接返回，不重复扣减总数。
        if ("DELETED".equals(comment.getStatus())) {
            return buildResp(
                    postId,
                    commentId,
                    0L,
                    currentCount
            );
        }

        if (!"NORMAL".equals(comment.getStatus())) {
            throw commentNotFound();
        }

        boolean isRoot = comment.getRootId() == null;

        LambdaQueryWrapper<PostComment> countQuery =
                new LambdaQueryWrapper<>();

        countQuery.eq(PostComment::getPostId, postId)
                .eq(PostComment::getStatus, "NORMAL");

        if (isRoot) {
            countQuery.and(q -> q
                    .eq(PostComment::getId, commentId)
                    .or()
                    .eq(PostComment::getRootId, commentId)
            );
        } else {
            countQuery.eq(PostComment::getId, commentId);
        }

        long deleteCount = commentMapper.selectCount(countQuery);

        if (deleteCount <= 0 || currentCount < deleteCount) {
            throw new IllegalStateException(
                    "评论记录与总数不一致，请检查数据库"
            );
        }

        LambdaUpdateWrapper<PostComment> deleteUpdate =
                new LambdaUpdateWrapper<>();

        deleteUpdate.eq(PostComment::getPostId, postId)
                .eq(PostComment::getStatus, "NORMAL");

        if (isRoot) {
            deleteUpdate.and(q -> q
                    .eq(PostComment::getId, commentId)
                    .or()
                    .eq(PostComment::getRootId, commentId)
            );
        } else {
            deleteUpdate.eq(PostComment::getId, commentId);
        }

        // 保留记录ID和回复关系，清空正文并标记删除。
        deleteUpdate.set(PostComment::getStatus, "DELETED")
                .set(PostComment::getContent, "")
                .set(PostComment::getUpdateTime, new Date());

        int affected = commentMapper.update(null, deleteUpdate);

        if ((long) affected != deleteCount) {
            throw new IllegalStateException("删除评论失败，请重试");
        }

        long remainingCount = currentCount - deleteCount;

        LambdaUpdateWrapper<Ootd> postUpdate =
                new LambdaUpdateWrapper<>();

        postUpdate.eq(Ootd::getId, postId)
                .set(Ootd::getCommentCount, remainingCount);

        if (postMapper.update(null, postUpdate) != 1) {
            throw new IllegalStateException("更新评论总数失败");
        }

        return buildResp(
                postId,
                commentId,
                deleteCount,
                remainingCount
        );
    }

    private PostCommentDeleteResp buildResp(
            Long postId,
            Long commentId,
            long deletedCount,
            long commentCount
    ) {
        PostCommentDeleteResp resp = new PostCommentDeleteResp();
        resp.setPostId(postId);
        resp.setCommentId(commentId);
        resp.setDeleted(true);
        resp.setDeletedCount(deletedCount);
        resp.setCommentCount(commentCount);
        return resp;
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
                "评论不存在"
        );
    }
}
