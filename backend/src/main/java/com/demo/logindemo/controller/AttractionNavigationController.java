package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionNavigationTargetResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionNavigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attractions")
public class AttractionNavigationController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionNavigationController.class);

    private final AttractionNavigationService navigationService;

    public AttractionNavigationController(
            AttractionNavigationService navigationService
    ) {
        this.navigationService = navigationService;
    }

    @GetMapping("/{attractionId}/navigation-target")
    public ResponseEntity<Result<AttractionNavigationTargetResp>>
    getTarget(
            @PathVariable("attractionId") Long attractionId,
            @RequestParam(
                    value = "travelMode",
                    defaultValue = "WALKING"
            ) String travelMode
    ) {
        try {
            AttractionNavigationTargetResp data =
                    navigationService.getTarget(
                            attractionId,
                            travelMode
                    );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionNavigationTargetResp> result =
                    Result.fail(
                            e.getReason() == null
                                    ? "请求失败"
                                    : e.getReason()
                    );

            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(
                    e.getStatusCode()
            ).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<AttractionNavigationTargetResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "查询导航目的地失败，attractionId={}，travelMode={}",
                    attractionId,
                    travelMode,
                    e
            );

            Result<AttractionNavigationTargetResp> result =
                    Result.fail("查询导航目的地失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}