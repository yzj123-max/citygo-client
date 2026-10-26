package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostCollectResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostCollectService;
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
@RequestMapping("/api/posts")
public class PostCollectController {

    private static final Logger log =
            LoggerFactory.getLogger(PostCollectController.class);

    private final PostCollectService collectService;

    public PostCollectController(PostCollectService collectService) {
        this.collectService = collectService;
    }

    @PutMapping("/{postId}/collect")
    public ResponseEntity<Result<PostCollectResp>> collect(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("postId") Long postId
    ) {
        return handle(currentUserId, postId, true);
    }

    @DeleteMapping("/{postId}/collect")
    public ResponseEntity<Result<PostCollectResp>> uncollect(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("postId") Long postId
    ) {
        return handle(currentUserId, postId, false);
    }

    private ResponseEntity<Result<PostCollectResp>> handle(
            Long currentUserId,
            Long postId,
            boolean wantCollected
    ) {
        try {
            PostCollectResp data = wantCollected
                    ? collectService.collect(currentUserId, postId)
                    : collectService.uncollect(currentUserId, postId);

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<PostCollectResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (Exception e) {
            log.error(
                    "收藏操作失败，userId={}，postId={}，wantCollected={}",
                    currentUserId,
                    postId,
                    wantCollected,
                    e
            );

            Result<PostCollectResp> result =
                    Result.fail("操作失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}