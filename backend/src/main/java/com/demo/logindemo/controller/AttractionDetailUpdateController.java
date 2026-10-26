package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionDetailUpdateReq;
import com.demo.logindemo.entity.AttractionDetail;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionDetailUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/attractions")
public class AttractionDetailUpdateController {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionDetailUpdateController.class);

    private final AttractionDetailUpdateService updateService;

    public AttractionDetailUpdateController(
            AttractionDetailUpdateService updateService
    ) {
        this.updateService = updateService;
    }

    @PutMapping("/{attractionId}/detail")
    public ResponseEntity<Result<AttractionDetail>> update(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("attractionId") Long attractionId,
            @RequestBody AttractionDetailUpdateReq req
    ) {
        try {
            AttractionDetail data = updateService.update(
                    currentUserId,
                    attractionId,
                    req
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionDetail> result = Result.fail(
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
                    Result.<AttractionDetail>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "保存景点介绍失败，userId={}，attractionId={}",
                    currentUserId,
                    attractionId,
                    e
            );

            Result<AttractionDetail> result =
                    Result.fail("保存景点介绍失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}