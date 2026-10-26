package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.AttractionPolicyUpdateReq;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.AttractionPolicy;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.AttractionPolicyMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AttractionPolicyUpdateService {

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

    private static final Set<String> SOURCE_TYPES =
            Set.of("OFFICIAL", "AMAP", "MANUAL", "UNKNOWN");

    private final AttractionAdminAccessService adminAccessService;
    private final AttractionMapper attractionMapper;
    private final AttractionPolicyMapper policyMapper;
    private final ObjectMapper objectMapper;

    public AttractionPolicyUpdateService(
            AttractionAdminAccessService adminAccessService,
            AttractionMapper attractionMapper,
            AttractionPolicyMapper policyMapper,
            ObjectMapper objectMapper
    ) {
        this.adminAccessService = adminAccessService;
        this.attractionMapper = attractionMapper;
        this.policyMapper = policyMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttractionPolicy update(
            Long currentUserId,
            Long attractionId,
            String policyType,
            AttractionPolicyUpdateReq req
    ) {
        adminAccessService.requireAdmin(currentUserId);

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        String type = normalize(policyType);

        if (!POLICY_VALUES.containsKey(type)) {
            throw new IllegalArgumentException(
                    "政策类型必须是TICKET、OPENING_HOURS、RESERVATION或PET"
            );
        }

        String value = normalize(req.getPolicyValue());

        if (!POLICY_VALUES.get(type).contains(value)) {
            throw new IllegalArgumentException(
                    "政策状态与当前政策类型不匹配"
            );
        }

        String sourceType = normalize(req.getSourceType());

        if (sourceType.isEmpty()) {
            sourceType = "UNKNOWN";
        }

        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("不支持的资料来源类型");
        }

        String content = validateText(
                req.getContent(), 12000, "政策说明"
        );

        String sourceName = validateText(
                req.getSourceName(), 255, "来源名称"
        );

        String sourceUrl = validateUrl(req.getSourceUrl());

        Map<String, Object> structuredData =
                validateStructuredData(req.getStructuredData());

        boolean unknown = "UNKNOWN".equals(value);

        if (!unknown && !req.isVerified()) {
            throw new IllegalArgumentException(
                    "未核实的政策只能保存为UNKNOWN"
            );
        }

        if (unknown && req.isVerified()) {
            throw new IllegalArgumentException(
                    "UNKNOWN表示政策尚不明确，请将verified设为false"
            );
        }

        if (req.isVerified()) {
            if (content == null) {
                throw new IllegalArgumentException(
                        "已核实政策必须填写详细说明"
                );
            }

            if ("UNKNOWN".equals(sourceType)
                    || sourceName == null
                    || sourceUrl == null) {
                throw new IllegalArgumentException(
                        "已核实政策必须填写有效的来源类型、名称和链接"
                );
            }
        }

        Date now = new Date();

        Date effectiveFrom = req.getEffectiveFrom();
        Date effectiveUntil = req.getEffectiveUntil();
        Date reviewDueAt = req.getReviewDueAt();

        if (effectiveFrom != null
                && effectiveUntil != null
                && !effectiveUntil.after(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "政策结束时间必须晚于开始时间"
            );
        }

        if (req.isVerified()
                && reviewDueAt != null
                && !reviewDueAt.after(now)) {
            throw new IllegalArgumentException(
                    "建议重新核实时间必须晚于当前时间"
            );
        }

        if (attractionId == null || attractionId <= 0) {
            throw notFound();
        }

        // 与其他景点资料维护操作使用相同的父记录锁。
        Attraction attraction = attractionMapper.selectOne(
                new LambdaQueryWrapper<Attraction>()
                        .eq(Attraction::getId, attractionId)
                        .last("FOR UPDATE")
        );

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw notFound();
        }

        AttractionPolicy existing = policyMapper.selectOne(
                new LambdaQueryWrapper<AttractionPolicy>()
                        .eq(
                                AttractionPolicy::getAttractionId,
                                attractionId
                        )
                        .eq(
                                AttractionPolicy::getPolicyType,
                                type
                        )
        );

        Date verifiedAt = req.isVerified() ? now : null;

        if (existing == null) {
            AttractionPolicy policy = new AttractionPolicy();

            policy.setAttractionId(attractionId);
            policy.setPolicyType(type);
            policy.setPolicyValue(value);
            policy.setContent(content);
            policy.setStructuredData(structuredData);
            policy.setSourceType(sourceType);
            policy.setSourceName(sourceName);
            policy.setSourceUrl(sourceUrl);
            policy.setVerifiedAt(verifiedAt);
            policy.setEffectiveFrom(effectiveFrom);
            policy.setEffectiveUntil(effectiveUntil);
            policy.setReviewDueAt(reviewDueAt);
            policy.setStatus("ACTIVE");
            policy.setCreateTime(now);
            policy.setUpdateTime(now);

            if (policyMapper.insert(policy) != 1
                    || policy.getId() == null) {
                throw new IllegalStateException("保存景点政策失败");
            }

            return policy;
        }

        LambdaUpdateWrapper<AttractionPolicy> update =
                new LambdaUpdateWrapper<>();

        update.eq(AttractionPolicy::getId, existing.getId())
                .eq(
                        AttractionPolicy::getAttractionId,
                        attractionId
                )
                .set(AttractionPolicy::getPolicyType, type)
                .set(AttractionPolicy::getPolicyValue, value)
                .set(AttractionPolicy::getContent, content)
                .set(
                        AttractionPolicy::getSourceType,
                        sourceType
                )
                .set(
                        AttractionPolicy::getSourceName,
                        sourceName
                )
                .set(
                        AttractionPolicy::getSourceUrl,
                        sourceUrl
                )
                .set(
                        AttractionPolicy::getVerifiedAt,
                        verifiedAt
                )
                .set(
                        AttractionPolicy::getEffectiveFrom,
                        effectiveFrom
                )
                .set(
                        AttractionPolicy::getEffectiveUntil,
                        effectiveUntil
                )
                .set(
                        AttractionPolicy::getReviewDueAt,
                        reviewDueAt
                )
                .set(AttractionPolicy::getStatus, "ACTIVE")
                .set(AttractionPolicy::getUpdateTime, now);

        // 通过实体字段的JacksonTypeHandler写入JSON。
        // 单独使用ALWAYS策略允许清空原有JSON字段。
        update.set(
                AttractionPolicy::getStructuredData,
                structuredData,
                "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler"
        );

        policyMapper.update(null, update);

        AttractionPolicy saved =
                policyMapper.selectById(existing.getId());

        if (saved == null) {
            throw new IllegalStateException("读取政策保存结果失败");
        }

        return saved;
    }

    private Map<String, Object> validateStructuredData(
            Map<String, Object> input
    ) {
        if (input == null) {
            return null;
        }

        try {
            byte[] json = objectMapper.writeValueAsBytes(input);

            if (json.length > 32768) {
                throw new IllegalArgumentException(
                        "结构化政策信息不能超过32KB"
                );
            }

            return objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {
                    }
            );

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "结构化政策信息不是有效的JSON对象"
            );
        }
    }

    private String validateText(
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
                    fieldName + "不能超过" + maxLength + "个字符"
            );
        }

        return value;
    }

    private String validateUrl(String input) {
        String value = validateText(
                input, 4000, "来源链接"
        );

        if (value == null) {
            return null;
        }

        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();

            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }

            return value;

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "来源链接必须是有效的HTTP或HTTPS地址"
            );
        }
    }

    private String normalize(String input) {
        return input == null
                ? ""
                : input.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "景点不存在或暂不可编辑"
        );
    }
}