package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AttractionPolicyUpdateReq;
import com.demo.logindemo.entity.AttractionPolicy;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AttractionPolicyUpdateService;
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
public class AttractionPolicyUpdateController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    AttractionPolicyUpdateController.class
            );

    private final AttractionPolicyUpdateService updateService;

    public AttractionPolicyUpdateController(
            AttractionPolicyUpdateService updateService
    ) {
        this.updateService = updateService;
    }

    @PutMapping("/{attractionId}/policies/{policyType}")
    public ResponseEntity<Result<AttractionPolicy>> update(
            @RequestAttribute("userId") Long currentUserId,
            @PathVariable("attractionId") Long attractionId,
            @PathVariable("policyType") String policyType,
            @RequestBody AttractionPolicyUpdateReq req
    ) {
        try {
            AttractionPolicy data = updateService.update(
                    currentUserId,
                    attractionId,
                    policyType,
                    req
            );

            return ResponseEntity.ok(Result.success(data));

        } catch (ResponseStatusException e) {
            Result<AttractionPolicy> result = Result.fail(
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
                    Result.<AttractionPolicy>fail(
                            e.getMessage()
                    )
            );

        } catch (Exception e) {
            log.error(
                    "保存景点政策失败，userId={}，attractionId={}，policyType={}",
                    currentUserId,
                    attractionId,
                    policyType,
                    e
            );

            Result<AttractionPolicy> result =
                    Result.fail("保存景点政策失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}