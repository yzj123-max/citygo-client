package com.demo.logindemo.controller;

import com.demo.logindemo.dto.PostPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.MyPostQueryService;
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
public class MyPostQueryController {

    private static final Logger log =
            LoggerFactory.getLogger(MyPostQueryController.class);

    private final MyPostQueryService queryService;

    public MyPostQueryController(MyPostQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/posts")
    public ResponseEntity<Result<PostPageResp>> getMyPosts(
            @RequestAttribute("userId") Long currentUserId,
            @RequestParam(value = "page", defaultValue = "1")
            long page,
            @RequestParam(value = "pageSize", defaultValue = "20")
            long pageSize
    ) {
        try {
            PostPageResp data = queryService.getMyPosts(
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
                    "查询我的帖子失败，userId={}，page={}，pageSize={}",
                    currentUserId,
                    page,
                    pageSize,
                    e
            );

            Result<PostPageResp> result =
                    Result.fail("查询我的帖子失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}