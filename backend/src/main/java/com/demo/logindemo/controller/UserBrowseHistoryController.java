package com.demo.logindemo.controller;

import com.demo.logindemo.dto.BrowseHistoryItemResp;
import com.demo.logindemo.dto.BrowseHistoryPageResp;
import com.demo.logindemo.dto.BrowseHistoryRecordReq;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.UserBrowseHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api/me/browse-history")
public class UserBrowseHistoryController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    UserBrowseHistoryController.class
            );

    private final UserBrowseHistoryService historyService;

    public UserBrowseHistoryController(
            UserBrowseHistoryService historyService
    ) {
        this.historyService = historyService;
    }

    /**
     * 记录一次景点浏览。
     * POST /api/me/browse-history
     */
    @PostMapping
    public ResponseEntity<Result<BrowseHistoryItemResp>> record(
            @RequestAttribute("userId") Long userId,
            @RequestBody BrowseHistoryRecordReq req
    ) {
        return execute(
                () -> historyService.recordView(userId, req)
        );
    }

    /**
     * 分页查询自己的景点浏览记录。
     * GET /api/me/browse-history
     */
    @GetMapping
    public ResponseEntity<Result<BrowseHistoryPageResp>> list(
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
                () -> historyService.getMyHistory(
                        userId,
                        page,
                        pageSize
                )
        );
    }

    /**
     * 删除自己的一条景点浏览记录。
     * 地址中填写historyId，不是景点ID。
     * DELETE /api/me/browse-history/{historyId}
     */
    @DeleteMapping("/{historyId}")
    public ResponseEntity<Result<String>> deleteOne(
            @RequestAttribute("userId") Long userId,
            @PathVariable("historyId") Long historyId
    ) {
        return execute(() -> {
            historyService.deleteOne(userId, historyId);
            return "删除成功";
        });
    }

    /**
     * 清空自己的全部景点浏览记录。
     * DELETE /api/me/browse-history
     */
    @DeleteMapping
    public ResponseEntity<Result<String>> clear(
            @RequestAttribute("userId") Long userId
    ) {
        return execute(() -> {
            historyService.clearMyHistory(userId);
            return "浏览记录已清空";
        });
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
            log.error("浏览记录操作失败", e);

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
