package com.demo.logindemo.service;

import com.demo.logindemo.dto.UserRelationPageResp;
import com.demo.logindemo.dto.UserSearchResp;
import com.demo.logindemo.mapper.UserMapper;
import com.demo.logindemo.mapper.UserRelationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserRelationService {

    private static final Set<String> SUPPORTED_TYPES =
            Set.of("FOLLOWING", "FOLLOWERS", "FRIENDS");

    private final UserRelationMapper relationMapper;
    private final UserMapper userMapper;

    public UserRelationService(
            UserRelationMapper relationMapper,
            UserMapper userMapper
    ) {
        this.relationMapper = relationMapper;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public UserRelationPageResp getList(
            Long currentUserId,
            String type,
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

        String normalizedType = type == null
                ? ""
                : type.trim().toUpperCase(Locale.ROOT);

        if (!SUPPORTED_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("不支持的用户列表类型");
        }

        if (page < 1) {
            throw new IllegalArgumentException("页码必须从1开始");
        }

        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量必须在1到100之间");
        }

        long offset;
        try {
            offset = Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("页码过大");
        }

        long total = relationMapper.countUsers(
                currentUserId,
                normalizedType
        );

        List<UserSearchResp> records = offset >= total
                ? List.of()
                : relationMapper.selectUsers(
                currentUserId,
                normalizedType,
                offset,
                pageSize
        );

        UserRelationPageResp resp = new UserRelationPageResp();
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
}