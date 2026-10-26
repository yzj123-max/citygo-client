package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionPoiResolveReq;
import com.demo.logindemo.dto.AttractionPoiResolveResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionPoiResolveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/attractions")
public class AttractionPoiResolveController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionPoiResolveController.class);

    private final AttractionPoiResolveService resolveService;

    public AttractionPoiResolveController(
            AttractionPoiResolveService resolveService
    ) {
        this.resolveService = resolveService;
    }

    @PostMapping("/resolve")
    public ResponseEntity<Result<AttractionPoiResolveResp>> resolve(
            @RequestAttribute("userId") Long currentUserId,
            @RequestBody AttractionPoiResolveReq req
    ) {
        try {
            AttractionPoiResolveResp data =
                    resolveService.resolve(currentUserId, req);

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionPoiResolveResp> result =
                    Result.fail(
                            e.getReason() == null
                                    ? "景点处理失败"
                                    : e.getReason()
                    );

            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(
                    e.getStatusCode()
            ).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<AttractionPoiResolveResp>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "处理景点关联失败，userId={}，exceptionType={}",
                    currentUserId,
                    e.getClass().getSimpleName()
            );

            Result<AttractionPoiResolveResp> result =
                    Result.fail("景点处理失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}