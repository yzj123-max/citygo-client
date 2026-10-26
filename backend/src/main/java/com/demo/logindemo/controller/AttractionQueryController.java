package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attractions")
public class AttractionQueryController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionQueryController.class);

    private final AttractionQueryService queryService;

    public AttractionQueryController(
            AttractionQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<Result<AttractionPageResp>> getList(
            @RequestParam(
                    value = "city",
                    required = false
            ) String city,

            @RequestParam(
                    value = "keyword",
                    required = false
            ) String keyword,

            @RequestParam(
                    value = "categoryCode",
                    required = false
            ) String categoryCode,

            @RequestParam(
                    value = "ticketStatus",
                    required = false
            ) String ticketStatus,

            @RequestParam(
                    value = "petPolicy",
                    required = false
            ) String petPolicy,

            @RequestParam(
                    value = "page",
                    defaultValue = "1"
            ) long page,

            @RequestParam(
                    value = "pageSize",
                    defaultValue = "20"
            ) long pageSize
    ) {
        try {
            AttractionPageResp data = queryService.getList(
                    city,
                    keyword,
                    categoryCode,
                    ticketStatus,
                    petPolicy,
                    page,
                    pageSize
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<AttractionPageResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error("查询景点列表失败", e);

            Result<AttractionPageResp> result =
                    Result.fail("查询景点列表失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}