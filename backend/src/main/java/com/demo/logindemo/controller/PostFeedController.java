package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.PostFeedService;
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
@RequestMapping("/api/posts")
public class PostFeedController {

    private static final Logger log =
            LoggerFactory.getLogger(PostFeedController.class);

    private final PostFeedService feedService;

    public PostFeedController(PostFeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ResponseEntity<Result<PostPageResp>> getFeed(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        try {
            PostPageResp data = feedService.getFeed(
                    currentUserId,
                    page,
                    pageSize
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<PostPageResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<PostPageResp>fail(e.getMessage())
            );

        } catch (Exception e) {
            log.error(
                    "查询帖子列表失败，userId={}，page={}，pageSize={}",
                    currentUserId,
                    page,
                    pageSize,
                    e
            );

            Result<PostPageResp> result =
                    Result.fail("查询失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}