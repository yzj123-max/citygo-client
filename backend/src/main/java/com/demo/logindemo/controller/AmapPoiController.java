package com.demo.logindemo.controller;

import com.demo.logindemo.dto.AmapPoiResp;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.service.AmapPoiService;
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

import java.math.BigDecimal;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/amap/pois")
public class AmapPoiController {

    private static final Logger log =
            LoggerFactory.getLogger(AmapPoiController.class);

    private final AmapPoiService poiService;

    public AmapPoiController(AmapPoiService poiService) {
        this.poiService = poiService;
    }

    /**
     * 关键词搜索。
     */
    @GetMapping("/search")
    public ResponseEntity<Result<AmapPoiResp>> search(
            @RequestParam(
                    value = "keywords",
                    required = false
            ) String keywords,

            @RequestParam(
                    value = "region",
                    required = false
            ) String region,

            @RequestParam(
                    value = "types",
                    defaultValue = "110000"
            ) String types,

            @RequestParam(
                    value = "cityLimit",
                    defaultValue = "false"
            ) boolean cityLimit,

            @RequestParam(
                    value = "page",
                    defaultValue = "1"
            ) int page,

            @RequestParam(
                    value = "pageSize",
                    defaultValue = "10"
            ) int pageSize
    ) {
        return handle(
                "search",
                () -> poiService.search(
                        keywords,
                        region,
                        types,
                        cityLimit,
                        page,
                        pageSize
                )
        );
    }

    /**
     * 根据高德POI ID查询详情。
     * 注意：这里不是本地attractions表的数字ID。
     */
    @GetMapping("/{poiId}")
    public ResponseEntity<Result<AmapPoiResp>> detail(
            @PathVariable("poiId") String poiId
    ) {
        return handle(
                "detail",
                () -> poiService.detail(poiId)
        );
    }

    /**
     * 根据手机提供的当前位置查询附近景点。
     */
    @GetMapping("/nearby")
    public ResponseEntity<Result<AmapPoiResp>> nearby(
            @RequestParam("longitude")
            BigDecimal longitude,

            @RequestParam("latitude")
            BigDecimal latitude,

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
        return handle(
                "nearby",
                () -> poiService.nearby(
                        longitude,
                        latitude,
                        radius,
                        page,
                        pageSize
                )
        );
    }

    private ResponseEntity<Result<AmapPoiResp>> handle(
            String operation,
            Supplier<AmapPoiResp> action
    ) {
        try {
            return ResponseEntity.ok(
                    Result.success(action.get())
            );

        } catch (ResponseStatusException e) {
            Result<AmapPoiResp> result = Result.fail(
                    e.getReason() == null
                            ? "高德查询失败"
                            : e.getReason()
            );

            result.setCode(e.getStatusCode().value());

            return ResponseEntity.status(
                    e.getStatusCode()
            ).body(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Result.<AmapPoiResp>fail(e.getMessage())
            );

        } catch (Exception e) {
            // 不打印请求URL或原始响应，避免泄露Web服务Key。
            log.error(
                    "高德POI查询异常，operation={}，exceptionType={}",
                    operation,
                    e.getClass().getSimpleName()
            );

            Result<AmapPoiResp> result =
                    Result.fail("高德查询失败，请查看后端日志");

            result.setCode(500);

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(result);
        }
    }
}
