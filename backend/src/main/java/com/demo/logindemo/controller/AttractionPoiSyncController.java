package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionPoiSyncResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.mapper.UserMapper;
import com.demo.logindemo.service.AttractionPoiSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/attractions")
public class AttractionPoiSyncController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionPoiSyncController.class);

    private final AttractionPoiSyncService syncService;
    private final UserMapper userMapper;

    @Value("${attractions.admin-user-ids:}")
    private String adminUserIds;

    public AttractionPoiSyncController(
            AttractionPoiSyncService syncService,
            UserMapper userMapper
    ) {
        this.syncService = syncService;
        this.userMapper = userMapper;
    }

    @PostMapping("/{attractionId}/sync-poi")
    public ResponseEntity<Result<AttractionPoiSyncResp>> sync(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("attractionId") Long attractionId
    ) {
        try {
            requireAdmin(currentUserId);

            AttractionPoiSyncResp data =
                    syncService.sync(attractionId);

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionPoiSyncResp> result = Result.fail(
                    e.getReason() == null
                            ? "请求失败"
                            : e.getReason()
            );

            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(
                    e.getStatusCode()
            ).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<AttractionPoiSyncResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "同步景点资料失败，userId={}，attractionId={}，exceptionType={}",
                    currentUserId,
                    attractionId,
                    e.getClass().getSimpleName()
            );

            Result<AttractionPoiSyncResp> result =
                    Result.fail("同步失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }

    private void requireAdmin(Long currentUserId) {
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

        // 未配置时不允许任何账号调用。
        if (adminUserIds == null || adminUserIds.isBlank()) {
            return ids;
        }

        for (String part : adminUserIds.split(",", -1)) {
            String value = part.trim();

            try {
                long id = Long.parseLong(value);

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