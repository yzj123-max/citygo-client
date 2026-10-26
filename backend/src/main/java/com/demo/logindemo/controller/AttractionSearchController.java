package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionSearchPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attractions")
public class AttractionSearchController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionSearchController.class);

    private final AttractionSearchService searchService;

    public AttractionSearchController(
            AttractionSearchService searchService
    ) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<Result<AttractionSearchPageResp>> search(
            @RequestParam("keyword") String keyword,

            @RequestParam(
                    value = "city",
                    required = false
            ) String city,

            @RequestParam(
                    value = "categoryCode",
                    required = false
            ) String categoryCode,

            @RequestParam(
                    value = "page",
                    defaultValue = "1"
            ) int page,

            @RequestParam(
                    value = "pageSize",
                    defaultValue = "10"
            ) int pageSize
    ) {
        try {
            AttractionSearchPageResp data =
                    searchService.search(
                            keyword,
                            city,
                            categoryCode,
                            page,
                            pageSize
                    );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionSearchPageResp> result =
                    Result.fail(
                            e.getReason() == null
                                    ? "搜索失败"
                                    : e.getReason()
                    );

            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(
                    e.getStatusCode()
            ).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<AttractionSearchPageResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "景点搜索异常，exceptionType={}",
                    e.getClass().getSimpleName()
            );

            Result<AttractionSearchPageResp> result =
                    Result.fail("景点搜索失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}