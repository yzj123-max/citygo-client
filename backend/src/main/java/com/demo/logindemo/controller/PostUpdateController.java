package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostDetailResp;
import com.demo.logindemo.dto.PostUpdateReq;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/posts")
public class PostUpdateController {

    private static final Logger log =
            LoggerFactory.getLogger(PostUpdateController.class);

    private final PostUpdateService updateService;

    public PostUpdateController(PostUpdateService updateService) {
        this.updateService = updateService;
    }

    @PutMapping("/{postId}")
    public ResponseEntity<Result<PostDetailResp>> update(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("postId") Long postId,
            @RequestBody PostUpdateReq req
    ) {
        try {
            PostDetailResp data = updateService.update(
                    currentUserId,
                    postId,
                    req
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<PostDetailResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<PostDetailResp>fail(e.getMessage())
            );

        } catch (Exception e) {
            log.error(
                    "编辑帖子失败，userId={}，postId={}",
                    currentUserId,
                    postId,
                    e
            );

            Result<PostDetailResp> result =
                    Result.fail("编辑失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}