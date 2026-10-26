package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.AttractionDetailUpdateReq;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.AttractionDetail;
import com.demo.logindemo.mapper.AttractionDetailMapper;
import com.demo.logindemo.mapper.AttractionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class AttractionDetailUpdateService {

    private final AttractionAdminAccessService adminAccessService;
    private final AttractionMapper attractionMapper;
    private final AttractionDetailMapper detailMapper;

    public AttractionDetailUpdateService(
            AttractionAdminAccessService adminAccessService,
            AttractionMapper attractionMapper,
            AttractionDetailMapper detailMapper
    ) {
        this.adminAccessService = adminAccessService;
        this.attractionMapper = attractionMapper;
        this.detailMapper = detailMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttractionDetail update(
            Long currentUserId,
            Long attractionId,
            AttractionDetailUpdateReq req
    ) {
        adminAccessService.requireAdmin(currentUserId);

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        if (attractionId == null || attractionId <= 0) {
            throw notFound();
        }

        String summary = validateText(
                req.getSummary(), 500, "简短介绍"
        );

        String description = validateText(
                req.getDescription(), 12000, "详细介绍"
        );

        String highlights = validateText(
                req.getHighlights(), 6000, "特色与看点"
        );

        String suggestedDuration = validateText(
                req.getSuggestedDuration(), 255, "建议游玩时长"
        );

        String bestSeason = validateText(
                req.getBestSeason(), 255, "建议游玩季节"
        );

        String trafficTips = validateText(
                req.getTrafficTips(), 4000, "交通建议"
        );

        String visitTips = validateText(
                req.getVisitTips(), 6000, "游玩注意事项"
        );

        String sourceName = validateText(
                req.getSourceName(), 255, "来源名称"
        );

        String sourceUrl = validateUrl(req.getSourceUrl());

        if (req.isVerified()) {
            if (sourceName == null || sourceUrl == null) {
                throw new IllegalArgumentException(
                        "标记已核实时，请填写来源名称和有效的来源链接"
                );
            }

            if (summary == null && description == null) {
                throw new IllegalArgumentException(
                        "标记已核实时，请至少填写简短介绍或详细介绍"
                );
            }
        }

        // 锁定景点，串行处理同一景点的资料修改。
        Attraction attraction = attractionMapper.selectOne(
                new LambdaQueryWrapper<Attraction>()
                        .eq(Attraction::getId, attractionId)
                        .last("FOR UPDATE")
        );

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw notFound();
        }

        AttractionDetail existing = detailMapper.selectOne(
                new LambdaQueryWrapper<AttractionDetail>()
                        .eq(
                                AttractionDetail::getAttractionId,
                                attractionId
                        )
        );

        Date now = new Date();
        Date verifiedAt = req.isVerified() ? now : null;

        if (existing == null) {
            AttractionDetail detail = new AttractionDetail();

            detail.setAttractionId(attractionId);
            detail.setSummary(summary);
            detail.setDescription(description);
            detail.setHighlights(highlights);
            detail.setSuggestedDuration(suggestedDuration);
            detail.setBestSeason(bestSeason);
            detail.setTrafficTips(trafficTips);
            detail.setVisitTips(visitTips);
            detail.setSourceName(sourceName);
            detail.setSourceUrl(sourceUrl);
            detail.setVerifiedAt(verifiedAt);
            detail.setCreateTime(now);
            detail.setUpdateTime(now);

            if (detailMapper.insert(detail) != 1
                    || detail.getId() == null) {
                throw new IllegalStateException("保存景点介绍失败");
            }

            return detail;
        }

        // 显式set允许把原有字段清空为NULL。
        LambdaUpdateWrapper<AttractionDetail> update =
                new LambdaUpdateWrapper<>();

        update.eq(AttractionDetail::getId, existing.getId())
                .eq(AttractionDetail::getAttractionId, attractionId)
                .set(AttractionDetail::getSummary, summary)
                .set(AttractionDetail::getDescription, description)
                .set(AttractionDetail::getHighlights, highlights)
                .set(
                        AttractionDetail::getSuggestedDuration,
                        suggestedDuration
                )
                .set(AttractionDetail::getBestSeason, bestSeason)
                .set(AttractionDetail::getTrafficTips, trafficTips)
                .set(AttractionDetail::getVisitTips, visitTips)
                .set(AttractionDetail::getSourceName, sourceName)
                .set(AttractionDetail::getSourceUrl, sourceUrl)
                .set(AttractionDetail::getVerifiedAt, verifiedAt)
                .set(AttractionDetail::getUpdateTime, now);

        // 重复提交相同内容也视为成功。
        detailMapper.update(null, update);

        AttractionDetail saved =
                detailMapper.selectById(existing.getId());

        if (saved == null) {
            throw new IllegalStateException("读取保存结果失败");
        }

        return saved;
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

        if (value.getBytes(StandardCharsets.UTF_8).length > 65535) {
            throw new IllegalArgumentException(
                    fieldName + "内容过长"
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

            boolean valid =
                    scheme != null
                            && ("http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme))
                            && uri.getHost() != null
                            && uri.getUserInfo() == null;

            if (!valid) {
                throw new IllegalArgumentException(
                        "来源链接必须是有效的HTTP或HTTPS地址"
                );
            }

            return value;

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "来源链接必须是有效的HTTP或HTTPS地址"
            );
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "景点不存在或暂不可编辑"
        );
    }
}