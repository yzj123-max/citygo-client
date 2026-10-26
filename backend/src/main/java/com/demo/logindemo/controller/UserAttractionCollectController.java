package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionCollectPageResp;
import com.demo.logindemo.dto.AttractionCollectResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.UserAttractionCollectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api/me/attraction-collections")
public class UserAttractionCollectController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    UserAttractionCollectController.class
            );

    private final UserAttractionCollectService collectService;

    public UserAttractionCollectController(
            UserAttractionCollectService collectService
    ) {
        this.collectService = collectService;
    }

    /**
     * 分页查询自己的景点收藏。
     * GET /api/me/attraction-collections
     */
    @GetMapping
    public ResponseEntity<Result<AttractionCollectPageResp>> list(
            @RequestAttribute("userId") Long userId,
            @RequestParam(
                    value = "page",
                    defaultValue = "1"
            ) long page,
            @RequestParam(
                    value = "pageSize",
                    defaultValue = "20"
            ) long pageSize
    ) {
        return execute(
                () -> collectService.getMyCollections(
                        userId,
                        page,
                        pageSize
                )
        );
    }

    /**
     * 查询自己是否收藏了某个景点。
     * GET /api/me/attraction-collections/{attractionId}
     */
    @GetMapping("/{attractionId}")
    public ResponseEntity<Result<AttractionCollectResp>> status(
            @RequestAttribute("userId") Long userId,
            @PathVariable("attractionId") Integer attractionId
    ) {
        return execute(
                () -> collectService.getStatus(
                        userId,
                        attractionId
                )
        );
    }

    /**
     * 收藏景点，无需请求正文。
     * PUT /api/me/attraction-collections/{attractionId}
     */
    @PutMapping("/{attractionId}")
    public ResponseEntity<Result<AttractionCollectResp>> collect(
            @RequestAttribute("userId") Long userId,
            @PathVariable("attractionId") Integer attractionId
    ) {
        return execute(
                () -> collectService.collect(
                        userId,
                        attractionId
                )
        );
    }

    /**
     * 取消收藏，无需请求正文。
     * DELETE /api/me/attraction-collections/{attractionId}
     */
    @DeleteMapping("/{attractionId}")
    public ResponseEntity<Result<AttractionCollectResp>> cancel(
            @RequestAttribute("userId") Long userId,
            @PathVariable("attractionId") Integer attractionId
    ) {
        return execute(
                () -> collectService.cancel(
                        userId,
                        attractionId
                )
        );
    }

    private <T> ResponseEntity<Result<T>> execute(
            Supplier<T> action
    ) {
        try {
            return ResponseEntity.ok(
                    Result.success(action.get())
            );

        } catch (ResponseStatusException e) {
            return failure(
                    e.getStatusCode().value(),
                    e.getReason() == null
                            ? "请求失败"
                            : e.getReason()
            );

        } catch (IllegalArgumentException e) {
            return failure(400, e.getMessage());

        } catch (Exception e) {
            log.error("景点收藏操作失败", e);

            return failure(
                    500,
                    "服务暂时异常，请稍后重试"
            );
        }
    }

    private <T> ResponseEntity<Result<T>> failure(
            int status,
            String message
    ) {
        Result<T> result = Result.fail(message);
        result.setCode(status);

        return ResponseEntity
                .status(status)
                .body(result);
    }
}