package com.demo.logindemo.controller;

import com.demo.logindemo.dto.UserRelationPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.UserRelationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
public class UserRelationController {

    private static final Logger log =
            LoggerFactory.getLogger(UserRelationController.class);

    private final UserRelationService relationService;

    public UserRelationController(UserRelationService relationService) {
        this.relationService = relationService;
    }

    @GetMapping("/following")
    public ResponseEntity<Result<UserRelationPageResp>> following(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return getList(currentUserId, "FOLLOWING", page, pageSize);
    }

    @GetMapping("/followers")
    public ResponseEntity<Result<UserRelationPageResp>> followers(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return getList(currentUserId, "FOLLOWERS", page, pageSize);
    }

    @GetMapping("/friends")
    public ResponseEntity<Result<UserRelationPageResp>> friends(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return getList(currentUserId, "FRIENDS", page, pageSize);
    }

    private ResponseEntity<Result<UserRelationPageResp>> getList(
            Long currentUserId,
            String type,
            long page,
            long pageSize
    ) {
        try {
            UserRelationPageResp data = relationService.getList(
                    currentUserId,
                    type,
                    page,
                    pageSize
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<UserRelationPageResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<UserRelationPageResp>fail(e.getMessage())
            );

        } catch (Exception e) {
            log.error(
                    "查询用户关系列表失败，userId={}，type={}，page={}，pageSize={}",
                    currentUserId, type, page, pageSize, e
            );

            Result<UserRelationPageResp> result =
                    Result.fail("查询失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}
