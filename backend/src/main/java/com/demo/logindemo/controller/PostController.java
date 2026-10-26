package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostDetailResp;
import com.demo.logindemo.dto.PostPublishReq;
import com.demo.logindemo.dto.PostPublishResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostDetailService;
import com.demo.logindemo.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final Logger log =
            LoggerFactory.getLogger(PostController.class);

    private final PostService postService;
    private final PostDetailService detailService;

    public PostController(
            PostService postService,
            PostDetailService detailService
    ) {
        this.postService = postService;
        this.detailService = detailService;
    }

    // 发布帖子
    @PostMapping
    public ResponseEntity<Result<PostPublishResp>> publish(
            @RequestAttribute("userId") Long userId,
            @RequestBody PostPublishReq req
    ) {
        try {
            PostPublishResp result =
                    postService.publish(userId, req);

            return ResponseEntity.ok(
                    Result.success(result)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<PostPublishResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "发布帖子失败，userId={}",
                    userId,
                    e
            );

            Result<PostPublishResp> result =
                    Result.fail("发布失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(result);
        }
    }

    // 查询帖子详情
    @GetMapping("/{postId}")
    public ResponseEntity<Result<PostDetailResp>> getDetail(
            @PathVariable("postId") Long postId,
            @RequestAttribute("userId") Long userId
    ) {
        try {
            PostDetailResp result =
                    detailService.getDetail(postId, userId);

            return ResponseEntity.ok(
                    Result.success(result)
            );

        } catch (ResponseStatusException e) {
            int status = e.getStatusCode().value();

            Result<PostDetailResp> result = Result.fail(
                    e.getReason() == null
                            ? "帖子不可访问"
                            : e.getReason()
            );

            result.setCode(status);

            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(result);

        } catch (Exception e) {
            log.error(
                    "查询帖子详情失败，postId={}，userId={}",
                    postId,
                    userId,
                    e
            );

            Result<PostDetailResp> result =
                    Result.fail("查询失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(result);
        }
    }
}