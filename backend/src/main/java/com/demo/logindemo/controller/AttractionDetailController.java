package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionDetailResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attractions")
public class AttractionDetailController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionDetailController.class);

    private final AttractionDetailService detailService;

    public AttractionDetailController(
            AttractionDetailService detailService
    ) {
        this.detailService = detailService;
    }

    @GetMapping("/{attractionId}")
    public ResponseEntity<Result<AttractionDetailResp>> getDetail(
            @PathVariable("attractionId") Long attractionId
    ) {
        try {
            AttractionDetailResp data =
                    detailService.getDetail(attractionId);

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionDetailResp> result = Result.fail(
                    e.getReason() == null ? "请求失败" : e.getReason()
            );
            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(e.getStatusCode()).body(result);

        } catch (Exception e) {
            log.error(
                    "查询景点详情失败，attractionId={}",
                    attractionId,
                    e
            );

            Result<AttractionDetailResp> result =
                    Result.fail("查询景点详情失败，请查看后端日志");
            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}