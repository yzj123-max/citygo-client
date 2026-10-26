package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostCollectResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.entity.PostCollect;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.PostCollectMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

@Service
public class PostCollectService {

    private final OotdMapper postMapper;
    private final PostCollectMapper collectMapper;
    private final UserMapper userMapper;
    private final PostAccessService accessService;

    public PostCollectService(
            OotdMapper postMapper,
            PostCollectMapper collectMapper,
            UserMapper userMapper,
            PostAccessService accessService
    ) {
        this.postMapper = postMapper;
        this.collectMapper = collectMapper;
        this.userMapper = userMapper;
        this.accessService = accessService;
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public PostCollectResp collect(Long currentUserId, Long postId) {
        return changeCollect(currentUserId, postId, true);
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public PostCollectResp uncollect(Long currentUserId, Long postId) {
        return changeCollect(currentUserId, postId, false);
    }

    private PostCollectResp changeCollect(
            Long currentUserId,
            Long postId,
            boolean wantCollected
    ) {
        validateUser(currentUserId);

        if (postId == null || postId <= 0) {
            throw notFound();
        }

        // 锁定帖子，保证收藏记录和收藏总数同步更新。
        Ootd post = postMapper.selectOne(
                new LambdaQueryWrapper<Ootd>()
                        .eq(Ootd::getId, postId)
                        .last("FOR UPDATE")
        );

        if (post == null || !accessService.canView(post, currentUserId)) {
            throw notFound();
        }

        PostCollect existing = collectMapper.selectOne(
                new LambdaQueryWrapper<PostCollect>()
                        .eq(PostCollect::getPostId, postId)
                        .eq(PostCollect::getUserId, currentUserId)
        );

        long count = post.getCollectCount() == null
                ? 0L
                : post.getCollectCount();

        if (count < 0) {
            throw new IllegalStateException("帖子收藏总数异常");
        }

        boolean changed = false;

        if (wantCollected && existing == null) {
            if (count == Long.MAX_VALUE) {
                throw new IllegalStateException("帖子收藏总数超出限制");
            }

            PostCollect record = new PostCollect();
            record.setPostId(postId);
            record.setUserId(currentUserId);
            record.setCreateTime(new Date());

            if (collectMapper.insert(record) != 1) {
                throw new IllegalStateException("保存收藏失败");
            }

            count++;
            changed = true;

        } else if (!wantCollected && existing != null) {
            if (count == 0) {
                throw new IllegalStateException(
                        "收藏记录与总数不一致，请检查数据库"
                );
            }

            if (collectMapper.deleteById(existing.getId()) != 1) {
                throw new IllegalStateException("取消收藏失败");
            }

            count--;
            changed = true;
        }

        if (changed) {
            LambdaUpdateWrapper<Ootd> update =
                    new LambdaUpdateWrapper<>();

            update.eq(Ootd::getId, postId)
                    .set(Ootd::getCollectCount, count);

            if (postMapper.update(null, update) != 1) {
                throw new IllegalStateException("更新收藏总数失败");
            }
        }

        PostCollectResp resp = new PostCollectResp();
        resp.setPostId(postId);
        resp.setCollected(wantCollected);
        resp.setCollectCount(count);

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