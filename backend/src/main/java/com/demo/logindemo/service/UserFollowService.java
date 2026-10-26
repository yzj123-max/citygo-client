package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.UserSearchResp;
import com.demo.logindemo.entity.UserFollow;
import com.demo.logindemo.mapper.UserFollowMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@Service
public class UserFollowService {

    private final UserFollowMapper followMapper;
    private final UserMapper userMapper;
    private final UserSearchService searchService;

    public UserFollowService(
            UserFollowMapper followMapper,
            UserMapper userMapper,
            UserSearchService searchService
    ) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.searchService = searchService;
    }

    // 关注用户：重复请求仍保持关注，不会变成取消关注。
    public UserSearchResp follow(
            Long currentUserId,
            Long targetUserId
    ) {
        validateUsers(currentUserId, targetUserId);

        UserFollow relation = new UserFollow();
        relation.setFollowerId(currentUserId);
        relation.setFolloweeId(targetUserId);
        relation.setCreateTime(new Date());

        try {
            if (followMapper.insert(relation) != 1) {
                throw new IllegalStateException("关注失败");
            }
        } catch (DuplicateKeyException e) {
            // 数据库唯一约束阻止重复关注。
            // 已经关注时直接返回当前关系状态。
        }

        return getRelation(currentUserId, targetUserId);
    }

    // 取消关注：不存在关注记录时也视为成功。
    public UserSearchResp unfollow(
            Long currentUserId,
            Long targetUserId
    ) {
        validateUsers(currentUserId, targetUserId);

        LambdaQueryWrapper<UserFollow> query =
                new LambdaQueryWrapper<>();

        query.eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFolloweeId, targetUserId);

        followMapper.delete(query);

        return getRelation(currentUserId, targetUserId);
    }

    private void validateUsers(
            Long currentUserId,
            Long targetUserId
    ) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }

        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException(
                    "目标用户ID必须为正整数"
            );
        }

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException(
                    "不能关注或取消关注自己"
            );
        }

        if (userMapper.selectById(targetUserId) == null) {
            throw new IllegalArgumentException(
                    "目标用户不存在"
            );
        }
    }

    private UserSearchResp getRelation(
            Long currentUserId,
            Long targetUserId
    ) {
        List<UserSearchResp> users =
                searchService.searchByUserId(
                        currentUserId,
                        targetUserId
                );

        if (users.isEmpty()) {
            throw new IllegalArgumentException(
                    "目标用户不存在"
            );
        }

        return users.get(0);
    }
}