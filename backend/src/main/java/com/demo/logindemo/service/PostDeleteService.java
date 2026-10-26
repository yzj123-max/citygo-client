package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.PostDeleteResp;
import com.demo.logindemo.entity.Ootd;
import com.demo.logindemo.mapper.OotdMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Objects;

@Service
public class PostDeleteService {

    private final OotdMapper postMapper;
    private final UserMapper userMapper;

    public PostDeleteService(
            OotdMapper postMapper,
            UserMapper userMapper
    ) {
        this.postMapper = postMapper;
        this.userMapper = userMapper;
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public PostDeleteResp delete(
            Long currentUserId,
            Long postId
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

        // 与点赞、收藏、发表评论使用相同的帖子锁。
        Ootd post = postMapper.selectOne(
                new LambdaQueryWrapper<Ootd>()
                        .eq(Ootd::getId, postId)
                        .last("FOR UPDATE")
        );

        // 只允许作者删除，其他用户统一返回404。
        if (post == null
                || !Objects.equals(post.getUserId(), currentUserId)) {
            throw notFound();
        }

        Long version = post.getVersion();

        if (version == null || version < 0) {
            throw new IllegalStateException("帖子版本号异常");
        }

        // 重复删除直接返回，不重复修改版本号。
        if ("DELETED".equals(post.getStatus())) {
            return buildResp(postId, version);
        }

        if (!"PUBLISHED".equals(post.getStatus())) {
            throw notFound();
        }

        if (version == Long.MAX_VALUE) {
            throw new IllegalStateException("帖子版本号超出限制");
        }

        long nextVersion = version + 1;

        LambdaUpdateWrapper<Ootd> update =
                new LambdaUpdateWrapper<>();

        update.eq(Ootd::getId, postId)
                .eq(Ootd::getUserId, currentUserId)
                .eq(Ootd::getStatus, "PUBLISHED")
                .eq(Ootd::getVersion, version)
                .set(Ootd::getStatus, "DELETED")
                .set(Ootd::getVersion, nextVersion)
                .set(Ootd::getUpdateTime, new Date());

        if (postMapper.update(null, update) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "帖子状态已变化，请刷新后重试"
            );
        }

        return buildResp(postId, nextVersion);
    }

    private PostDeleteResp buildResp(
            Long postId,
            Long version
    ) {
        PostDeleteResp resp = new PostDeleteResp();
        resp.setPostId(postId);
        resp.setDeleted(true);
        resp.setVersion(version);
        return resp;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "帖子不存在或不可删除"
        );
    }
}