package com.demo.logindemo.service;

import com.demo.logindemo.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

@Service
public class AttractionAdminAccessService {

    private final UserMapper userMapper;

    @Value("${attractions.admin-user-ids:}")
    private String adminUserIds;

    public AttractionAdminAccessService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public void requireAdmin(Long currentUserId) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }

        Set<Long> allowedIds = readAdminIds();

        if (!allowedIds.contains(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账号没有景点管理权限"
            );
        }
    }

    private Set<Long> readAdminIds() {
        Set<Long> ids = new HashSet<>();

        // 未配置时，不允许任何账号管理景点。
        if (adminUserIds == null || adminUserIds.isBlank()) {
            return ids;
        }

        for (String part : adminUserIds.split(",", -1)) {
            try {
                long id = Long.parseLong(part.trim());

                if (id <= 0) {
                    throw new NumberFormatException();
                }

                ids.add(id);

            } catch (NumberFormatException e) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "景点管理员配置格式错误，请检查后端配置"
                );
            }
        }

        return ids;
    }
}