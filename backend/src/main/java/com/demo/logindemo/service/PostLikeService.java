package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostLikeResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostLike;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.PostLikeMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

@Service
public class PostLikeService {

    private final OotdMapper postMapper;
    private final PostLikeMapper likeMapper;
    private final UserMapper userMapper;
    private final PostAccessService accessService;

    public PostLikeService(
            OotdMapper postMapper,
            PostLikeMapper likeMapper,
            UserMapper userMapper,
            PostAccessService accessService
    ) {
        this.postMapper = postMapper;
        this.likeMapper = likeMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public PostLikeResp like(Long currentUserId, Long postId) {
        return changeLike(currentUserId, postId, true);
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public PostLikeResp unlike(Long currentUserId, Long postId) {
        return changeLike(currentUserId, postId, false);
    }

    private PostLikeResp changeLike(
            Long currentUserId,
            Long postId,
            boolean wantLiked
    ) {
        validateUser(currentUserId);

        if (postId == null || postId <= 0) {
            throw notFound();
        }

        // 锁定帖子，让同一帖子的点赞操作按顺序执行。
        // 点赞记录与总数在同一个事务中更新。
        Ootd post = postMapper.selectOne(
                new LambdaQueryWrapper<Ootd>()
                        .eq(Ootd::getId, postId)
                        .last("FOR UPDATE")
        );

        if (post == null || !accessService.canView(post, currentUserId)) {
            throw notFound();
        }

        PostLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, currentUserId)
        );

        long count = post.getLikeCount() == null
                ? 0L
                : post.getLikeCount();

        if (count < 0) {
            throw new IllegalStateException("帖子点赞总数异常");
        }

        boolean changed = false;

        if (wantLiked && existing == null) {
            if (count == Long.MAX_VALUE) {
                throw new IllegalStateException("帖子点赞总数超出限制");
            }

            PostLike record = new PostLike();
            record.setPostId(postId);
            record.setUserId(currentUserId);
            record.setCreateTime(new Date());

            if (likeMapper.insert(record) != 1) {
                throw new IllegalStateException("保存点赞失败");
            }

            count++;
            changed = true;

        } else if (!wantLiked && existing != null) {
            if (count == 0) {
                throw new IllegalStateException(
                        "点赞记录与总数不一致，请检查数据库"
                );
            }

            if (likeMapper.deleteById(existing.getId()) != 1) {
                throw new IllegalStateException("取消点赞失败");
            }

            count--;
            changed = true;
        }

        if (changed) {
            LambdaUpdateWrapper<Ootd> update =
                    new LambdaUpdateWrapper<>();

            update.eq(Ootd::getId, postId)
                    .set(Ootd::getLikeCount, count);

            if (postMapper.update(null, update) != 1) {
                throw new IllegalStateException("更新点赞总数失败");
            }
        }

        PostLikeResp resp = new PostLikeResp();
        resp.setPostId(postId);
        resp.setLiked(wantLiked);
        resp.setLikeCount(count);

        return resp;
    }

    private void validateUser(Long currentUserId) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "帖子不存在或不可见"
        );
    }
}