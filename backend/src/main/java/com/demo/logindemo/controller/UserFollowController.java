package com.demo.logindemo.controller;

import com.demo.logindemo.dto.UserSearchResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.UserFollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserFollowController {

    private static final Logger log =
            LoggerFactory.getLogger(UserFollowController.class);

    private final UserFollowService followService;

    public UserFollowController(
            UserFollowService followService
    ) {
        this.followService = followService;
    }

    // 关注用户
    @PutMapping("/{targetUserId}/follow")
    public ResponseEntity<Result<UserSearchResp>> follow(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("targetUserId") Long targetUserId
    ) {
        return handle(currentUserId, targetUserId, true);
    }

    // 取消关注
    @DeleteMapping("/{targetUserId}/follow")
    public ResponseEntity<Result<UserSearchResp>> unfollow(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("targetUserId") Long targetUserId
    ) {
        return handle(currentUserId, targetUserId, false);
    }

    private ResponseEntity<Result<UserSearchResp>> handle(
            Long currentUserId,
            Long targetUserId,
            boolean following
    ) {
        try {
            UserSearchResp result = following
                    ? followService.follow(
                    currentUserId,
                    targetUserId
            )
                    : followService.unfollow(
                    currentUserId,
                    targetUserId
            );

            return ResponseEntity.ok(
                    Result.success(result)
            );

        } catch (ResponseStatusException e) {
            Result<UserSearchResp> result = Result.fail(
                    e.getReason() == null
                            ? "请求失败"
                            : e.getReason()
            );

            result.setCode(e.getStatusCode().value());

            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<UserSearchResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "关注操作失败，currentUserId={}，targetUserId={}，following={}",
                    currentUserId,
                    targetUserId,
                    following,
                    e
            );

            Result<UserSearchResp> result =
                    Result.fail("操作失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(result);
        }
    }
}