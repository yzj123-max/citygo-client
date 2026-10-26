package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostCommentPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostCommentQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/posts")
public class PostCommentQueryController {

    private static final Logger log =
            LoggerFactory.getLogger(PostCommentQueryController.class);

    private final PostCommentQueryService queryService;

    public PostCommentQueryController(
            PostCommentQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<Result<PostCommentPageResp>> getComments(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("postId") Long postId,
            @RequestParam(value = "rootId", required = false)
            Long rootId,
            @RequestParam(value = "page", defaultValue = "1")
            long page,
            @RequestParam(value = "pageSize", defaultValue = "20")
            long pageSize
    ) {
        try {
            PostCommentPageResp data = queryService.getComments(
                    currentUserId,
                    postId,
                    rootId,
                    page,
                    pageSize
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<PostCommentPageResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<PostCommentPageResp>fail(e.getMessage())
            );

        } catch (Exception e) {
            log.error(
                    "查询评论失败，userId={}，postId={}，rootId={}",
                    currentUserId,
                    postId,
                    rootId,
                    e
            );

            Result<PostCommentPageResp> result =
                    Result.fail("查询评论失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}