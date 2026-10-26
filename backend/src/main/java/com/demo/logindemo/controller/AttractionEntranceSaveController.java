package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionEntranceSaveReq;
import com.demo.logindemo.entity.AttractionEntrance;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionEntranceSaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api/admin/attractions")
public class AttractionEntranceSaveController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AttractionEntranceSaveController.class
            );

    private final AttractionEntranceSaveService saveService;

    public AttractionEntranceSaveController(
            AttractionEntranceSaveService saveService
    ) {
        this.saveService = saveService;
    }

    @PostMapping("/{attractionId}/entrances")
    public ResponseEntity<Result<AttractionEntrance>> create(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("attractionId") Long attractionId,
            @RequestBody AttractionEntranceSaveReq req
    ) {
        return handle(
                currentUserId,
                attractionId,
                () -> saveService.create(
                        currentUserId,
                        attractionId,
                        req
                )
        );
    }

    @PutMapping("/{attractionId}/entrances/{entranceId}")
    public ResponseEntity<Result<AttractionEntrance>> update(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("attractionId") Long attractionId,
            @PathVariable("entranceId") Long entranceId,
            @RequestBody AttractionEntranceSaveReq req
    ) {
        return handle(
                currentUserId,
                attractionId,
                () -> saveService.update(
                        currentUserId,
                        attractionId,
                        entranceId,
                        req
                )
        );
    }

    private ResponseEntity<Result<AttractionEntrance>> handle(
            Long currentUserId,
            Long attractionId,
            Supplier<AttractionEntrance> action
    ) {
        try {
            return ResponseEntity.ok(
                    Result.success(action.get())
            );

        } catch (ResponseStatusException e) {
            Result<AttractionEntrance> result = Result.fail(
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
                    Result.<AttractionEntrance>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "保存景点入口失败，userId={}，attractionId={}",
                    currentUserId,
                    attractionId,
                    e
            );

            Result<AttractionEntrance> result =
                    Result.fail("保存入口失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}