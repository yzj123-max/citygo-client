package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AttractionDetailResp;
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
import com.demo.logindemo.util.TosUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
public class AttractionDetailService {

    private static final Logger log =
            LoggerFactory.getLogger(AttractionDetailService.class);

    private static final List<String> POLICY_TYPES = List.of(
            "TICKET",
            "OPENING_HOURS",
            "RESERVATION",
            "PET"
    );

    private static final Map<String, Set<String>> POLICY_VALUES =
            Map.of(
                    "TICKET",
                    Set.of("FREE", "PAID", "PARTIAL", "UNKNOWN"),

                    "OPENING_HOURS",
                    Set.of(
                            "SCHEDULED",
                            "TEMPORARILY_CLOSED",
                            "UNKNOWN"
                    ),

                    "RESERVATION",
                    Set.of(
                            "REQUIRED",
                            "OPTIONAL",
                            "NOT_REQUIRED",
                            "UNKNOWN"
                    ),

                    "PET",
                    Set.of(
                            "ALLOWED",
                            "CONDITIONAL",
                            "NOT_ALLOWED",
                            "UNKNOWN"
                    )
            );

    private final AttractionMapper attractionMapper;
    private final AttractionDetailMapper detailMapper;
    private final AttractionImageMapper imageMapper;
    private final AttractionPolicyMapper policyMapper;
    private final AttractionEntranceMapper entranceMapper;
    private final TosUtil tosUtil;

    @Value("${volc.tos.presignExpireSeconds:1800}")
    private long previewExpiresInSeconds;

    public AttractionDetailService(
            AttractionMapper attractionMapper,
            AttractionDetailMapper detailMapper,
            AttractionImageMapper imageMapper,
            AttractionPolicyMapper policyMapper,
            AttractionEntranceMapper entranceMapper,
            TosUtil tosUtil
    ) {
        this.attractionMapper = attractionMapper;
        this.detailMapper = detailMapper;
        this.imageMapper = imageMapper;
        this.policyMapper = policyMapper;
        this.entranceMapper = entranceMapper;
        this.tosUtil = tosUtil;
    }

    @Transactional(readOnly = true)
    public AttractionDetailResp getDetail(Long attractionId) {
        if (attractionId == null || attractionId <= 0) {
            throw notFound();
        }

        Attraction attraction =
                attractionMapper.selectById(attractionId);

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw notFound();
        }

        AttractionDetailResp resp = new AttractionDetailResp();

        resp.setAttractionId(attraction.getId());
        resp.setName(attraction.getAttrName());
        resp.setProvince(attraction.getProvince());
        resp.setCity(attraction.getCity());
        resp.setDistrict(attraction.getDistrict());
        resp.setLevel(attraction.getLevel());
        resp.setAddress(attraction.getAddress());
        resp.setLongitude(attraction.getLongitude());
        resp.setLatitude(attraction.getLatitude());
        resp.setCoordSystem(attraction.getCoordSystem());
        resp.setCategoryCode(attraction.getCategoryCode());
        resp.setCategoryName(attraction.getCategoryName());
        resp.setPhone(attraction.getPhone());

        resp.setCollectedInfo(mapCollectedInfo(attraction));
        fillDescription(resp, attraction);
        if (!hasText(resp.getHighlights())) resp.setHighlights(attraction.getFeature());
        resp.setImages(loadImages(attractionId));
        resp.setPolicies(loadPolicies(attractionId));

        boolean canNavigate = validCoordinates(
                attraction.getLongitude(),
                attraction.getLatitude(),
                attraction.getCoordSystem()
        );

        if (!canNavigate) {
            canNavigate = hasUsableEntrance(attractionId);
        }

        resp.setCanNavigate(canNavigate);

        return resp;
    }

    public static AttractionDetailResp.CollectedInfo mapCollectedInfo(Attraction attraction) {
        AttractionDetailResp.CollectedInfo collected = new AttractionDetailResp.CollectedInfo();
        collected.setTicketPrice(attraction.getTicketPrice());
        collected.setDiscountPolicy(attraction.getDiscountPolicy());
        collected.setOpenTime(attraction.getOpenTime());
        collected.setFeature(attraction.getFeature());
        collected.setPetFriendlyLevel(attraction.getPetFriendlyLevel());
        collected.setPetFriendlinessScore(attraction.getPetNote1());
        collected.setPetPolicy(attraction.getPetPolicy());
        // source是原表宠物采集资料的来源，不赋给门票或详细介绍。
        collected.setPetPolicySource(attraction.getSource());
        // 保留“2026年10月”等原始精度，不伪造成某天的核实时间。
        collected.setRecordedReviewTime(attraction.getVerifyTime());
        return collected;
    }

    private void fillDescription(
            AttractionDetailResp resp,
            Attraction attraction
    ) {
        AttractionDetail detail = detailMapper.selectOne(
                new LambdaQueryWrapper<AttractionDetail>()
                        .eq(
                                AttractionDetail::getAttractionId,
                                attraction.getId()
                        )
        );

        if (detail == null) {
            // 只将原有特色作为介绍兜底，不推断票务或宠物政策。
            resp.setSummary(attraction.getFeature());
            resp.setDescriptionSourceName(attraction.getSource());
            return;
        }

        resp.setSummary(
                hasText(detail.getSummary())
                        ? detail.getSummary()
                        : attraction.getFeature()
        );

        resp.setDescription(detail.getDescription());
        resp.setHighlights(detail.getHighlights());
        resp.setSuggestedDuration(detail.getSuggestedDuration());
        resp.setBestSeason(detail.getBestSeason());
        resp.setTrafficTips(detail.getTrafficTips());
        resp.setVisitTips(detail.getVisitTips());

        // 来源和核实时间仅对应详情表中的介绍资料。
        resp.setDescriptionSourceName(detail.getSourceName());
        resp.setDescriptionSourceUrl(
                safeHttpUrl(detail.getSourceUrl())
        );
        resp.setDescriptionVerifiedAt(detail.getVerifiedAt());
    }

    private List<AttractionDetailResp.ImageItem> loadImages(
            Long attractionId
    ) {
        List<AttractionImage> records = imageMapper.selectList(
                new LambdaQueryWrapper<AttractionImage>()
                        .eq(
                                AttractionImage::getAttractionId,
                                attractionId
                        )
                        .eq(AttractionImage::getStatus, "ACTIVE")
                        .orderByDesc(AttractionImage::getIsCover)
                        .orderByAsc(AttractionImage::getSortOrder)
                        .orderByAsc(AttractionImage::getId)
        );

        List<AttractionDetailResp.ImageItem> result =
                new ArrayList<>();

        for (AttractionImage image : records) {
            String storageType = normalize(image.getStorageType());
            String url;
            Long expiresInSeconds = null;

            if ("TOS".equals(storageType)) {
                if (!hasText(image.getObjectKey())) {
                    continue;
                }

                try {
                    url = tosUtil.generatePresignedUrl(
                            image.getObjectKey()
                    );
                } catch (Exception e) {
                    // 一张图片异常不影响整个景点详情。
                    log.warn(
                            "景点图片签名失败，attractionId={}，imageId={}",
                            attractionId,
                            image.getId()
                    );
                    continue;
                }

                url = safeHttpUrl(url);
                expiresInSeconds = previewExpiresInSeconds;

            } else if ("EXTERNAL".equals(storageType)) {
                url = safeHttpUrl(image.getImageUrl());

            } else {
                continue;
            }

            if (url == null) {
                continue;
            }

            AttractionDetailResp.ImageItem item =
                    new AttractionDetailResp.ImageItem();

            item.setImageId(image.getId());
            item.setUrl(url);
            item.setExpiresInSeconds(expiresInSeconds);
            item.setCaption(image.getCaption());

            // 即使历史数据有多个封面，也只向前端返回一个。
            // 没有指定封面时，以第一张有效图片作为封面。
            item.setCover(result.isEmpty());

            item.setSourceName(image.getSourceName());
            item.setSourceUrl(safeHttpUrl(image.getSourceUrl()));

            result.add(item);
        }

        return result;
    }

    private List<AttractionDetailResp.PolicyItem> loadPolicies(
            Long attractionId
    ) {
        List<AttractionPolicy> records = policyMapper.selectList(
                new LambdaQueryWrapper<AttractionPolicy>()
                        .eq(
                                AttractionPolicy::getAttractionId,
                                attractionId
                        )
                        .eq(AttractionPolicy::getStatus, "ACTIVE")
        );

        Map<String, AttractionPolicy> index = new HashMap<>();

        for (AttractionPolicy policy : records) {
            String type = normalize(policy.getPolicyType());

            if (POLICY_VALUES.containsKey(type)) {
                index.put(type, policy);
            }
        }

        Date now = new Date();

        List<AttractionDetailResp.PolicyItem> result =
                new ArrayList<>();

        // 固定返回四种政策，缺失的明确标记UNKNOWN。
        for (String type : POLICY_TYPES) {
            AttractionPolicy policy = index.get(type);

            AttractionDetailResp.PolicyItem item =
                    new AttractionDetailResp.PolicyItem();

            item.setPolicyType(type);
            item.setPolicyValue("UNKNOWN");
            item.setSourceType("UNKNOWN");
            item.setTemporalStatus("UNSPECIFIED");
            item.setContent("暂未核实，建议联系景区确认");

            if (policy != null) {
                fillPolicy(item, policy, type, now);
            }

            result.add(item);
        }

        return result;
    }

    private void fillPolicy(
            AttractionDetailResp.PolicyItem item,
            AttractionPolicy policy,
            String type,
            Date now
    ) {
        String value = normalize(policy.getPolicyValue());

        if (!POLICY_VALUES.get(type).contains(value)) {
            value = "UNKNOWN";
        }

        String sourceType = normalize(policy.getSourceType());

        if (!Set.of(
                "OFFICIAL", "AMAP", "MANUAL", "UNKNOWN"
        ).contains(sourceType)) {
            sourceType = "UNKNOWN";
        }

        item.setSourceType(sourceType);
        item.setSourceName(policy.getSourceName());
        item.setSourceUrl(safeHttpUrl(policy.getSourceUrl()));
        item.setVerifiedAt(policy.getVerifiedAt());
        item.setEffectiveFrom(policy.getEffectiveFrom());
        item.setEffectiveUntil(policy.getEffectiveUntil());

        item.setReviewRequired(
                policy.getReviewDueAt() != null
                        && !policy.getReviewDueAt().after(now)
        );

        Date from = policy.getEffectiveFrom();
        Date until = policy.getEffectiveUntil();

        // 错误的生效时间范围不作为当前政策使用。
        if (from != null
                && until != null
                && !until.after(from)) {
            item.setPolicyValue("UNKNOWN");
            item.setContent("政策有效时间信息异常，建议联系景区确认");
            return;
        }

        if (until != null && !until.after(now)) {
            item.setTemporalStatus("EXPIRED");
            item.setPolicyValue("UNKNOWN");
            item.setContent("已有政策已过有效期，建议联系景区确认");
            return;
        }

        if (from != null && from.after(now)) {
            item.setTemporalStatus("UPCOMING");
            item.setPolicyValue("UNKNOWN");
            item.setContent("已有政策尚未生效，当前政策请联系景区确认");
            return;
        }

        if (from != null || until != null) {
            item.setTemporalStatus("CURRENT");
        }

        item.setPolicyValue(value);

        if ("UNKNOWN".equals(value)) {
            item.setContent("暂未核实，建议联系景区确认");
            return;
        }

        item.setContent(
                hasText(policy.getContent())
                        ? policy.getContent()
                        : "具体条件请查看资料来源或联系景区确认"
        );

        item.setStructuredData(policy.getStructuredData());
    }

    private boolean hasUsableEntrance(Long attractionId) {
        List<AttractionEntrance> entrances =
                entranceMapper.selectList(
                        new LambdaQueryWrapper<AttractionEntrance>()
                                .eq(
                                        AttractionEntrance::getAttractionId,
                                        attractionId
                                )
                                .eq(
                                        AttractionEntrance::getStatus,
                                        "ACTIVE"
                                )
                );

        for (AttractionEntrance entrance : entrances) {
            boolean supportsNavigation =
                    Boolean.TRUE.equals(
                            entrance.getSupportsWalking()
                    )
                            || Boolean.TRUE.equals(
                            entrance.getSupportsDriving()
                    );

            if (supportsNavigation && validCoordinates(
                    entrance.getLongitude(),
                    entrance.getLatitude(),
                    entrance.getCoordSystem()
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean validCoordinates(
            BigDecimal longitude,
            BigDecimal latitude,
            String coordSystem
    ) {
        if (longitude == null
                || latitude == null
                || !"GCJ02".equals(normalize(coordSystem))) {
            return false;
        }

        boolean inRange =
                longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                        && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                        && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                        && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;

        boolean zeroPair =
                longitude.signum() == 0 && latitude.signum() == 0;

        return inRange && !zeroPair;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "景点不存在或暂不可查看"
        );
    }
}
