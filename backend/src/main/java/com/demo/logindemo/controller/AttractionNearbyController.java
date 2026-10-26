package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionSearchPageResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionNearbyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/attractions")
public class AttractionNearbyController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionNearbyController.class);

    private final AttractionNearbyService nearbyService;

    public AttractionNearbyController(
            AttractionNearbyService nearbyService
    ) {
        this.nearbyService = nearbyService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<Result<AttractionSearchPageResp>> nearby(
            @RequestParam("longitude")
            BigDecimal longitude,

            @RequestParam("latitude")
            BigDecimal latitude,

            @RequestParam(
                    value = "coordSystem",
                    defaultValue = "GCJ02"
            ) String coordSystem,

            @RequestParam(
                    value = "radius",
                    defaultValue = "5000"
            ) int radius,

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
                    nearbyService.nearby(
                            longitude,
                            latitude,
                            coordSystem,
                            radius,
                            page,
                            pageSize
                    );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionSearchPageResp> result =
                    Result.fail(
                            e.getReason() == null
                                    ? "附近景点查询失败"
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
                    "附近景点查询异常，exceptionType={}",
                    e.getClass().getSimpleName()
            );

            Result<AttractionSearchPageResp> result =
                    Result.fail("附近景点查询失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}