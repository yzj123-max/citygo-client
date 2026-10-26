package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.UserSearchResp;
import com.demo.logindemo.entity.User;
import com.demo.logindemo.entity.UserFollow;
import com.demo.logindemo.mapper.UserFollowMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserSearchService {

    private final UserMapper userMapper;
    private final UserFollowMapper followMapper;

    public UserSearchService(
            UserMapper userMapper,
            UserFollowMapper followMapper
    ) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
    }

    // 按用户ID精确搜索，不要求双方已有关注关系。
    public List<UserSearchResp> searchByUserId(
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
                    "请输入有效的用户ID，必须是正整数"
            );
        }

        User target = userMapper.selectById(targetUserId);

        // 没有匹配用户时返回空列表。
        if (target == null) {
            return List.of();
        }

        UserSearchResp result = new UserSearchResp();

        result.setUserId(target.getId());

        String nickname = target.getNickname();

        result.setNickname(
                nickname == null || nickname.isBlank()
                        ? "用户" + target.getId()
                        : nickname
        );

        result.setAvatarUrl(target.getAvatarUrl());

        boolean following = false;
        boolean followedBy = false;

        // 搜索自己时，不计算关注或好友关系。
        if (!currentUserId.equals(target.getId())) {
            following = isFollowing(
                    currentUserId,
                    target.getId()
            );

            followedBy = isFollowing(
                    target.getId(),
                    currentUserId
            );
        }

        result.setFollowing(following);
        result.setFollowedBy(followedBy);
        result.setFriend(following && followedBy);

        return List.of(result);
    }

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
}