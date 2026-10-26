package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostCommentCreateReq;
import com.demo.logindemo.dto.PostCommentResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostCommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/posts")
public class PostCommentController {

    private static final Logger log =
            LoggerFactory.getLogger(PostCommentController.class);

    private final PostCommentService commentService;

    public PostCommentController(PostCommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<Result<PostCommentResp>> create(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("postId") Long postId,
            @RequestBody PostCommentCreateReq req
    ) {
        try {
            PostCommentResp data = commentService.create(
                    currentUserId,
                    postId,
                    req
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<PostCommentResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<PostCommentResp>fail(e.getMessage())
            );

        } catch (Exception e) {
            log.error(
                    "发表评论失败，userId={}，postId={}",
                    currentUserId,
                    postId,
                    e
            );

            Result<PostCommentResp> result =
                    Result.fail("发表评论失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}