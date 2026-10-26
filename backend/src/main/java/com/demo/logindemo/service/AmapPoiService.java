package com.demo.logindemo.service;

import com.demo.logindemo.dto.AmapPoiResp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AmapPoiService {

    private static final String BASE_URL =
            "https://restapi.amap.com/v5/place/";

    private static final String DEFAULT_TYPES = "110000";

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Object requestLock = new Object();

    private long lastRequestNanos;

    @Value("${amap.poi.web-key:}")
    private String webKey;

    // 单实例请求起始时间至少间隔500毫秒。
    // 实际可用配额仍以高德账号权限为准。
    @Value("${amap.poi.min-interval-ms:500}")
    private long minIntervalMs;

    public AmapPoiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 关键词搜索。
     *
     * region为空：全国搜索。
     * region不为空且cityLimit=true：限制在指定区域。
     * types为空：默认风景名胜分类110000。
     */
    public AmapPoiResp search(
            String keywords,
            String region,
            String types,
            boolean cityLimit,
            int page,
            int pageSize
    ) {
        validatePage(page, pageSize);

        String keyword = normalizeText(keywords);
        String searchRegion = normalizeText(region);

        if (keyword.length() > 80) {
            throw new IllegalArgumentException(
                    "搜索关键词不能超过80个字符"
            );
        }

        if (searchRegion.length() > 100) {
            throw new IllegalArgumentException("搜索区域过长");
        }

        if (cityLimit && searchRegion.isEmpty()) {
            throw new IllegalArgumentException(
                    "限定城市搜索时必须提供区域"
            );
        }

        Map<String, String> params = new LinkedHashMap<>();

        if (!keyword.isEmpty()) {
            params.put("keywords", keyword);
        }

        params.put("types", validateTypes(types));

        if (!searchRegion.isEmpty()) {
            params.put("region", searchRegion);
        }

        params.put("city_limit", String.valueOf(cityLimit));
        addPageParams(params, page, pageSize);

        return execute("text", params);
    }

    /**
     * 根据高德POI ID查询详情。
     * 此处一次查询一个POI，避免与本地景点ID混用。
     */
    public AmapPoiResp detail(String amapPoiId) {
        String poiId = normalizeText(amapPoiId);

        if (!poiId.matches("[A-Za-z0-9]{1,64}")) {
            throw new IllegalArgumentException(
                    "请输入有效的高德POI ID"
            );
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", poiId);

        return execute("detail", params);
    }

    /**
     * 附近景点搜索。
     * 传入高德GCJ02坐标，经度在前、纬度在后。
     */
    public AmapPoiResp nearby(
            BigDecimal longitude,
            BigDecimal latitude,
            int radius,
            int page,
            int pageSize
    ) {
        validatePage(page, pageSize);
        validateCoordinates(longitude, latitude);

        if (radius < 1 || radius > 50000) {
            throw new IllegalArgumentException(
                    "搜索半径必须在1到50000米之间"
            );
        }

        String location =
                coordinateText(longitude)
                        + ","
                        + coordinateText(latitude);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("location", location);
        params.put("radius", String.valueOf(radius));
        params.put("types", DEFAULT_TYPES);
        params.put("sortrule", "distance");

        addPageParams(params, page, pageSize);

        return execute("around", params);
    }

    /**
     * 安全解析商业扩展信息。
     * 高德未提供有效business对象时，返回空对象。
     */
    public AmapPoiResp.BusinessInfo readBusiness(
            AmapPoiResp.PoiItem poi
    ) {
        if (poi == null) {
            return new AmapPoiResp.BusinessInfo();
        }

        JsonNode business = poi.getBusiness();

        if (business == null || !business.isObject()) {
            return new AmapPoiResp.BusinessInfo();
        }

        try {
            return objectMapper.treeToValue(
                    business,
                    AmapPoiResp.BusinessInfo.class
            );
        } catch (JsonProcessingException e) {
            return new AmapPoiResp.BusinessInfo();
        }
    }

    private AmapPoiResp execute(
            String endpoint,
            Map<String, String> params
    ) {
        String key = normalizeText(webKey);

        if (key.isEmpty()
                || key.contains("这里替换")
                || key.contains("你自己的")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "尚未配置有效的高德Web服务Key"
            );
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("key", key);
        query.putAll(params);
        query.put("show_fields", "business,photos");
        query.put("output", "json");

        StringBuilder url = new StringBuilder(
                BASE_URL + endpoint + "?"
        );

        boolean first = true;

        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) {
                url.append("&");
            }

            url.append(encode(entry.getKey()))
                    .append("=")
                    .append(encode(entry.getValue()));

            first = false;
        }

        // 不打印完整URL，避免把Key写入日志。
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .GET()
                .build();

        synchronized (requestLock) {
            try {
                waitForRateLimit();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );

                if (response.statusCode() != 200) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "高德服务HTTP异常："
                                    + response.statusCode()
                    );
                }

                AmapPoiResp result = objectMapper.readValue(
                        response.body(),
                        AmapPoiResp.class
                );

                if (result == null) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "高德服务未返回有效数据"
                    );
                }

                if (!"1".equals(result.getStatus())) {
                    String code = result.getInfocode();

                    // 只返回错误码，不透传可能包含敏感信息的原始响应。
                    if (code == null || !code.matches("\\d{1,10}")) {
                        code = "UNKNOWN";
                    }

                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "高德查询失败，错误码：" + code
                    );
                }

                if (result.getPois() == null) {
                    result.setPois(new ArrayList<>());
                } else {
                    result.getPois().removeIf(poi -> poi == null);
                }

                return result;

            } catch (HttpTimeoutException e) {
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "高德服务响应超时，请稍后重试"
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "高德查询已中断，请重试"
                );

            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "高德返回数据格式异常"
                );

            } catch (IOException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "连接高德服务失败，请稍后重试"
                );
            }
        }
    }

    private void waitForRateLimit() throws InterruptedException {
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(
                Math.max(100L, minIntervalMs)
        );

        if (lastRequestNanos != 0) {
            long elapsed =
                    System.nanoTime() - lastRequestNanos;

            long remaining = intervalNanos - elapsed;

            if (remaining > 0) {
                TimeUnit.NANOSECONDS.sleep(remaining);
            }
        }

        lastRequestNanos = System.nanoTime();
    }

    private void addPageParams(
            Map<String, String> params,
            int page,
            int pageSize
    ) {
        params.put("page_num", String.valueOf(page));
        params.put("page_size", String.valueOf(pageSize));
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || page > 100) {
            throw new IllegalArgumentException(
                    "高德查询页码必须在1到100之间"
            );
        }

        if (pageSize < 1 || pageSize > 25) {
            throw new IllegalArgumentException(
                    "每页数量必须在1到25之间"
            );
        }
    }

    private String validateTypes(String types) {
        String value = normalizeText(types);

        if (value.isEmpty()) {
            return DEFAULT_TYPES;
        }

        if (value.length() > 139
                || !value.matches("\\d{6}(\\|\\d{6})*")) {
            throw new IllegalArgumentException(
                    "景点分类必须是六位分类码，多个分类用|分隔，最多20个"
            );
        }

        return value;
    }

    private void validateCoordinates(
            BigDecimal longitude,
            BigDecimal latitude
    ) {
        if (longitude == null || latitude == null) {
            throw new IllegalArgumentException("请提供当前位置坐标");
        }

        boolean valid =
                longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                        && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                        && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                        && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;

        boolean zeroPair =
                longitude.signum() == 0
                        && latitude.signum() == 0;

        if (!valid || zeroPair) {
            throw new IllegalArgumentException("当前位置坐标无效");
        }
    }

    private String coordinateText(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}