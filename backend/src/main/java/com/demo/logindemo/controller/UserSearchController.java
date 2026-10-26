package com.demo.logindemo.controller;

import com.demo.logindemo.dto.UserSearchResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.UserSearchService;
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

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {

    private static final Logger log =
            LoggerFactory.getLogger(UserSearchController.class);

    private final UserSearchService searchService;

    public UserSearchController(
            UserSearchService searchService
    ) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<Result<List<UserSearchResp>>> search(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam("userId") Long targetUserId
    ) {
        try {
            List<UserSearchResp> users =
                    searchService.searchByUserId(
                            currentUserId,
                            targetUserId
                    );

            return ResponseEntity.ok(
                    Result.success(users)
            );

        } catch (ResponseStatusException e) {
            Result<List<UserSearchResp>> result =
                    Result.fail(
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
                    Result.<List<UserSearchResp>>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "搜索用户失败，currentUserId={}，targetUserId={}",
                    currentUserId,
                    targetUserId,
                    e
            );

            Result<List<UserSearchResp>> result =
                    Result.fail("搜索失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(result);
        }
    }
}