package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostCommentDeleteResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostCommentDeleteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/posts")
public class PostCommentDeleteController {

    private static final Logger log =
            LoggerFactory.getLogger(PostCommentDeleteController.class);

    private final PostCommentDeleteService deleteService;

    public PostCommentDeleteController(
            PostCommentDeleteService deleteService
    ) {
        this.deleteService = deleteService;
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Result<PostCommentDeleteResp>> delete(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("postId") Long postId,
            @PathVariable("commentId") Long commentId
    ) {
        try {
            PostCommentDeleteResp data = deleteService.delete(
                    currentUserId,
                    postId,
                    commentId
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<PostCommentDeleteResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (Exception e) {
            log.error(
                    "删除评论失败，userId={}，postId={}，commentId={}",
                    currentUserId,
                    postId,
                    commentId,
                    e
            );

            Result<PostCommentDeleteResp> result =
                    Result.fail("删除评论失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}