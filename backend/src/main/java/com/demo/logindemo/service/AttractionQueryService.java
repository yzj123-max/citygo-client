package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AttractionCardResp;
import com.demo.logindemo.dto.AttractionPageResp;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.AttractionDetail;
import com.demo.logindemo.entity.AttractionEntrance;
import com.demo.logindemo.entity.AttractionImage;
import com.demo.logindemo.entity.AttractionPolicy;
import com.demo.logindemo.mapper.AttractionDetailMapper;
import com.demo.logindemo.mapper.AttractionEntranceMapper;
import com.demo.logindemo.mapper.AttractionImageMapper;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.AttractionPolicyMapper;
import com.demo.logindemo.mapper.AttractionQueryMapper;
import com.demo.logindemo.util.TosUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AttractionQueryService {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionQueryService.class);

    private static final Set<String> TICKET_VALUES =
            Set.of("FREE", "PAID", "PARTIAL");

    private static final Set<String> PET_VALUES =
            Set.of("ALLOWED", "CONDITIONAL", "NOT_ALLOWED");

    private final AttractionQueryMapper queryMapper;
    private final AttractionMapper attractionMapper;
    private final AttractionDetailMapper detailMapper;
    private final AttractionImageMapper imageMapper;
    private final AttractionPolicyMapper policyMapper;
    private final AttractionEntranceMapper entranceMapper;
    private final TosUtil tosUtil;

    @Value("${volc.tos.presignExpireSeconds:1800}")
    private long previewExpiresInSeconds;

    public AttractionQueryService(
            AttractionQueryMapper queryMapper,
            AttractionMapper attractionMapper,
            AttractionDetailMapper detailMapper,
            AttractionImageMapper imageMapper,
            AttractionPolicyMapper policyMapper,
            AttractionEntranceMapper entranceMapper,
            TosUtil tosUtil
    ) {
        this.queryMapper = queryMapper;
        this.attractionMapper = attractionMapper;
        this.detailMapper = detailMapper;
        this.imageMapper = imageMapper;
        this.policyMapper = policyMapper;
        this.entranceMapper = entranceMapper;
        this.tosUtil = tosUtil;
    }

    @Transactional(readOnly = true)
    public AttractionPageResp getList(
            String city,
            String keyword,
            String categoryCode,
            String ticketStatus,
            String petPolicy,
            long page,
            long pageSize
    ) {
        city = cleanText(city, 100, "城市");
        keyword = cleanText(keyword, 80, "搜索关键词");
        categoryCode = cleanText(categoryCode, 6, "分类编码");

        if (categoryCode != null
                && !categoryCode.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "景点分类编码必须为六位数字"
            );
        }

        ticketStatus = normalizeFilter(
                ticketStatus,
                TICKET_VALUES,
                "门票状态"
        );

        petPolicy = normalizeFilter(
                petPolicy,
                PET_VALUES,
                "宠物政策"
        );

        if (page < 1) {
            throw new IllegalArgumentException("页码必须从1开始");
        }

        if (pageSize < 1 || pageSize > 50) {
            throw new IllegalArgumentException(
                    "每页数量必须在1到50之间"
            );
        }

        long offset;

        try {
            offset = Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("页码过大");
        }

        Date now = new Date();

        long total = queryMapper.countMatching(
                city,
                keyword,
                categoryCode,
                ticketStatus,
                petPolicy,
                now
        );

        AttractionPageResp resp = new AttractionPageResp();
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(total);

        if (offset >= total) {
            resp.setHasMore(false);
            return resp;
        }

        List<Long> ids = queryMapper.selectMatchingIds(
                city,
                keyword,
                categoryCode,
                ticketStatus,
                petPolicy,
                now,
                offset,
                pageSize
        );

        if (ids.isEmpty()) {
            resp.setHasMore(false);
            return resp;
        }

        List<AttractionCardResp> records =
                buildCards(ids, now);

        resp.setRecords(records);
        resp.setHasMore(
                offset < total
                        && records.size() < total - offset
        );

        return resp;
    }

    private List<AttractionCardResp> buildCards(
            List<Long> ids,
            Date now
    ) {
        Map<Long, Attraction> attractions = new HashMap<>();

        for (Attraction attraction :
                attractionMapper.selectBatchIds(ids)) {
            attractions.put(attraction.getId(), attraction);
        }

        Map<Long, AttractionDetail> details = new HashMap<>();

        for (AttractionDetail detail : detailMapper.selectList(
                new LambdaQueryWrapper<AttractionDetail>()
                        .in(AttractionDetail::getAttractionId, ids)
        )) {
            details.put(detail.getAttractionId(), detail);
        }

        Map<Long, List<AttractionImage>> images = new HashMap<>();

        for (AttractionImage image : imageMapper.selectList(
                new LambdaQueryWrapper<AttractionImage>()
                        .in(AttractionImage::getAttractionId, ids)
                        .eq(AttractionImage::getStatus, "ACTIVE")
                        .orderByDesc(AttractionImage::getIsCover)
                        .orderByAsc(AttractionImage::getSortOrder)
                        .orderByAsc(AttractionImage::getId)
        )) {
            images.computeIfAbsent(
                    image.getAttractionId(),
                    key -> new ArrayList<>()
            ).add(image);
        }

        Map<Long, Map<String, AttractionPolicy>> policies =
                new HashMap<>();

        for (AttractionPolicy policy : policyMapper.selectList(
                new LambdaQueryWrapper<AttractionPolicy>()
                        .in(AttractionPolicy::getAttractionId, ids)
                        .eq(AttractionPolicy::getStatus, "ACTIVE")
                        .in(
                                AttractionPolicy::getPolicyType,
                                List.of("TICKET", "PET")
                        )
        )) {
            policies.computeIfAbsent(
                    policy.getAttractionId(),
                    key -> new HashMap<>()
            ).put(
                    policy.getPolicyType().toUpperCase(Locale.ROOT),
                    policy
            );
        }

        Set<Long> entranceNavigable = new java.util.HashSet<>();

        for (AttractionEntrance entrance :
                entranceMapper.selectList(
                        new LambdaQueryWrapper<AttractionEntrance>()
                                .in(
                                        AttractionEntrance::getAttractionId,
                                        ids
                                )
                                .eq(
                                        AttractionEntrance::getStatus,
                                        "ACTIVE"
                                )
                )) {
            if (usableEntrance(entrance)) {
                entranceNavigable.add(
                        entrance.getAttractionId()
                );
            }
        }

        List<AttractionCardResp> records = new ArrayList<>();

        // 按分页查询得到的ID顺序组装，保持推荐排序。
        for (Long id : ids) {
            Attraction attraction = attractions.get(id);

            if (attraction == null) {
                continue;
            }

            AttractionCardResp card = new AttractionCardResp();

            card.setAttractionId(id);
            card.setName(attraction.getAttrName());
            card.setProvince(attraction.getProvince());
            card.setCity(attraction.getCity());
            card.setDistrict(attraction.getDistrict());
            card.setLevel(attraction.getLevel());
            card.setAddress(attraction.getAddress());
            card.setCategoryCode(attraction.getCategoryCode());
            card.setCategoryName(attraction.getCategoryName());
            card.setLongitude(attraction.getLongitude());
            card.setLatitude(attraction.getLatitude());
            card.setCoordSystem(attraction.getCoordSystem());

            AttractionDetail detail = details.get(id);

            card.setSummary(
                    detail != null && hasText(detail.getSummary())
                            ? detail.getSummary()
                            : attraction.getFeature()
            );

            fillCover(
                    card,
                    images.getOrDefault(id, List.of())
            );

            Map<String, AttractionPolicy> ownPolicies =
                    policies.getOrDefault(id, Map.of());

            card.setTicketStatus(
                    currentPolicyValue(
                            ownPolicies.get("TICKET"),
                            TICKET_VALUES,
                            now
                    )
            );

            card.setPetPolicy(
                    currentPolicyValue(
                            ownPolicies.get("PET"),
                            PET_VALUES,
                            now
                    )
            );

            card.setCanNavigate(
                    validCoordinates(
                            attraction.getLongitude(),
                            attraction.getLatitude(),
                            attraction.getCoordSystem()
                    )
                            || entranceNavigable.contains(id)
            );

            // 当前接口未提供用户位置，不计算距离。
            card.setDistanceMeters(null);

            records.add(card);
        }

        return records;
    }

    private void fillCover(
            AttractionCardResp card,
            List<AttractionImage> images
    ) {
        for (AttractionImage image : images) {
            String type = image.getStorageType();
            String url;
            Long expires = null;

            if ("TOS".equalsIgnoreCase(type)) {
                if (!hasText(image.getObjectKey())) {
                    continue;
                }

                try {
                    url = safeHttpUrl(
                            tosUtil.generatePresignedUrl(
                                    image.getObjectKey()
                            )
                    );
                    expires = previewExpiresInSeconds;

                } catch (Exception e) {
                    log.warn(
                            "景点封面签名失败，imageId={}",
                            image.getId()
                    );
                    continue;
                }

            } else if ("EXTERNAL".equalsIgnoreCase(type)) {
                url = safeHttpUrl(image.getImageUrl());

            } else {
                continue;
            }

            if (url != null) {
                card.setCoverUrl(url);
                card.setCoverExpiresInSeconds(expires);
                return;
            }
        }
    }

    private String currentPolicyValue(
            AttractionPolicy policy,
            Set<String> allowedValues,
            Date now
    ) {
        if (policy == null
                || policy.getVerifiedAt() == null
                || policy.getVerifiedAt().after(now)) {
            return "UNKNOWN";
        }

        Date from = policy.getEffectiveFrom();
        Date until = policy.getEffectiveUntil();
        Date review = policy.getReviewDueAt();

        if (from != null && from.after(now)) {
            return "UNKNOWN";
        }

        if (until != null && !until.after(now)) {
            return "UNKNOWN";
        }

        if (from != null
                && until != null
                && !until.after(from)) {
            return "UNKNOWN";
        }

        if (review != null && !review.after(now)) {
            return "UNKNOWN";
        }

        String value = policy.getPolicyValue();

        if (value == null) {
            return "UNKNOWN";
        }

        value = value.toUpperCase(Locale.ROOT);

        return allowedValues.contains(value)
                ? value
                : "UNKNOWN";
    }

    private boolean usableEntrance(
            AttractionEntrance entrance
    ) {
        String type = entrance.getEntranceType();

        if (!hasText(type)
                || !Set.of("VISITOR", "VEHICLE", "PARKING")
                .contains(type.trim().toUpperCase(Locale.ROOT))
                || !hasText(entrance.getName())) {
            return false;
        }

        boolean supported =
                Boolean.TRUE.equals(entrance.getSupportsWalking())
                        || Boolean.TRUE.equals(
                        entrance.getSupportsDriving()
                );

        return supported && validCoordinates(
                entrance.getLongitude(),
                entrance.getLatitude(),
                entrance.getCoordSystem()
        );
    }

    private boolean validCoordinates(
            BigDecimal longitude,
            BigDecimal latitude,
            String coordSystem
    ) {
        if (longitude == null
                || latitude == null
                || coordSystem == null
                || !"GCJ02".equalsIgnoreCase(coordSystem.trim())) {
            return false;
        }

        boolean inRange =
                longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                        && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                        && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                        && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;

        return inRange
                && !(longitude.signum() == 0
                && latitude.signum() == 0);
    }

    private String normalizeFilter(
            String input,
            Set<String> allowedValues,
            String fieldName
    ) {
        String value = cleanText(input, 30, fieldName);

        if (value == null) {
            return null;
        }

        value = value.toUpperCase(Locale.ROOT);

        if (!"UNKNOWN".equals(value)
                && !allowedValues.contains(value)) {
            throw new IllegalArgumentException(
                    "不支持的" + fieldName + "：" + input
            );
        }

        return value;
    }

    private String cleanText(
            String input,
            int maxLength,
            String fieldName
    ) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String value = input.trim();

        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "长度不能超过" + maxLength
            );
        }

        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeHttpUrl(String input) {
        if (!hasText(input)) {
            return null;
        }

        try {
            URI uri = URI.create(input.trim());
            String scheme = uri.getScheme();

            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                return null;
            }

            return input.trim();

        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    /**
     * 根据景点ID批量查询卡片。
     * 只返回当前正常开放查询的景点。
     * 用于收藏列表，沿用已有封面、政策和导航状态处理。
     */
    @Transactional(readOnly = true)
    public List<AttractionCardResp> getCardsByIds(
            List<Long> attractionIds
    ) {
        if (attractionIds == null || attractionIds.isEmpty()) {
            return List.of();
        }

        List<Long> ids = attractionIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        if (ids.size() > 50) {
            throw new IllegalArgumentException(
                    "一次最多查询50个景点"
            );
        }

        Set<Long> activeIds = attractionMapper.selectList(
                        new LambdaQueryWrapper<Attraction>()
                                .select(Attraction::getId)
                                .in(Attraction::getId, ids)
                                .eq(Attraction::getStatus, "ACTIVE")
                ).stream()
                .map(Attraction::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<Long> availableIds = ids.stream()
                .filter(activeIds::contains)
                .toList();

        if (availableIds.isEmpty()) {
            return List.of();
        }

        return buildCards(availableIds, new Date());
    }
}