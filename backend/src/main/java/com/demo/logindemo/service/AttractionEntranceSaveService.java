package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.AttractionEntranceSaveReq;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.AttractionEntrance;
import com.demo.logindemo.mapper.AttractionEntranceMapper;
import com.demo.logindemo.mapper.AttractionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

@Service
public class AttractionEntranceSaveService {

    private static final Set<String> TYPES =
            Set.of("VISITOR", "VEHICLE", "PARKING");

    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "HIDDEN", "CLOSED");

    private final AttractionAdminAccessService adminAccessService;
    private final AttractionMapper attractionMapper;
    private final AttractionEntranceMapper entranceMapper;

    public AttractionEntranceSaveService(
            AttractionAdminAccessService adminAccessService,
            AttractionMapper attractionMapper,
            AttractionEntranceMapper entranceMapper
    ) {
        this.adminAccessService = adminAccessService;
        this.attractionMapper = attractionMapper;
        this.entranceMapper = entranceMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttractionEntrance create(
            Long currentUserId,
            Long attractionId,
            AttractionEntranceSaveReq req
    ) {
        return save(currentUserId, attractionId, null, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public AttractionEntrance update(
            Long currentUserId,
            Long attractionId,
            Long entranceId,
            AttractionEntranceSaveReq req
    ) {
        if (entranceId == null || entranceId <= 0) {
            throw entranceNotFound();
        }

        return save(currentUserId, attractionId, entranceId, req);
    }

    private AttractionEntrance save(
            Long currentUserId,
            Long attractionId,
            Long entranceId,
            AttractionEntranceSaveReq req
    ) {
        adminAccessService.requireAdmin(currentUserId);

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        String name = cleanText(req.getName(), 255, "入口名称");

        if (name == null) {
            throw new IllegalArgumentException("请填写入口名称");
        }

        String type = normalize(req.getEntranceType());

        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "入口类型必须是VISITOR、VEHICLE或PARKING"
            );
        }

        String status = normalize(req.getStatus());

        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "入口状态必须是ACTIVE、HIDDEN或CLOSED"
            );
        }

        if (!"GCJ02".equals(normalize(req.getCoordSystem()))) {
            throw new IllegalArgumentException(
                    "当前只接受GCJ02高德坐标"
            );
        }

        validateCoordinates(
                req.getLongitude(),
                req.getLatitude()
        );

        String poiId = cleanText(
                req.getAmapPoiId(), 64, "高德POI ID"
        );

        if (poiId != null
                && !poiId.matches("[A-Za-z0-9]{1,64}")) {
            throw new IllegalArgumentException(
                    "入口的高德POI ID格式错误"
            );
        }

        String address = cleanText(
                req.getAddress(), 512, "入口地址"
        );

        String notice = cleanText(
                req.getNotice(), 12000, "通行提示"
        );

        String sourceName = cleanText(
                req.getSourceName(), 255, "来源名称"
        );

        String sourceUrl = validateUrl(req.getSourceUrl());

        int priority = req.getPriority() == null
                ? 0
                : req.getPriority();

        if (priority < 0 || priority > 10000) {
            throw new IllegalArgumentException(
                    "入口优先级必须在0到10000之间"
            );
        }

        boolean walking =
                Boolean.TRUE.equals(req.getSupportsWalking());

        boolean driving =
                Boolean.TRUE.equals(req.getSupportsDriving());

        if (req.isVerified()
                && (sourceName == null || sourceUrl == null)) {
            throw new IllegalArgumentException(
                    "已核实入口必须填写来源名称和有效链接"
            );
        }

        if ("ACTIVE".equals(status)) {
            if (!req.isVerified()) {
                throw new IllegalArgumentException(
                        "未核实入口请先保存为HIDDEN，核实后再启用"
                );
            }

            if (!walking && !driving) {
                throw new IllegalArgumentException(
                        "启用的入口必须至少支持步行或驾车中的一种"
                );
            }
        }

        if (attractionId == null || attractionId <= 0) {
            throw attractionNotFound();
        }

        // 锁定父景点，串行处理同一景点的入口维护。
        Attraction attraction = attractionMapper.selectOne(
                new LambdaQueryWrapper<Attraction>()
                        .eq(Attraction::getId, attractionId)
                        .last("FOR UPDATE")
        );

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw attractionNotFound();
        }

        AttractionEntrance existing = null;

        if (entranceId != null) {
            existing = entranceMapper.selectOne(
                    new LambdaQueryWrapper<AttractionEntrance>()
                            .eq(AttractionEntrance::getId, entranceId)
                            .eq(
                                    AttractionEntrance::getAttractionId,
                                    attractionId
                            )
            );

            if (existing == null) {
                throw entranceNotFound();
            }
        }

        checkDuplicate(
                attractionId,
                entranceId,
                poiId,
                name,
                type,
                req.getLongitude(),
                req.getLatitude()
        );

        Date now = new Date();
        Date verifiedAt = req.isVerified() ? now : null;

        if (existing == null) {
            AttractionEntrance entrance = new AttractionEntrance();

            entrance.setAttractionId(attractionId);
            entrance.setName(name);
            entrance.setEntranceType(type);
            entrance.setAmapPoiId(poiId);
            entrance.setAddress(address);
            entrance.setLongitude(req.getLongitude());
            entrance.setLatitude(req.getLatitude());
            entrance.setCoordSystem("GCJ02");
            entrance.setSupportsWalking(walking);
            entrance.setSupportsDriving(driving);
            entrance.setPriority(priority);
            entrance.setNotice(notice);
            entrance.setSourceName(sourceName);
            entrance.setSourceUrl(sourceUrl);
            entrance.setVerifiedAt(verifiedAt);
            entrance.setStatus(status);
            entrance.setCreateTime(now);
            entrance.setUpdateTime(now);

            if (entranceMapper.insert(entrance) != 1
                    || entrance.getId() == null) {
                throw new IllegalStateException("保存入口失败");
            }

            return entrance;
        }

        LambdaUpdateWrapper<AttractionEntrance> update =
                new LambdaUpdateWrapper<>();

        update.eq(AttractionEntrance::getId, entranceId)
                .eq(
                        AttractionEntrance::getAttractionId,
                        attractionId
                )
                .set(AttractionEntrance::getName, name)
                .set(AttractionEntrance::getEntranceType, type)
                .set(AttractionEntrance::getAmapPoiId, poiId)
                .set(AttractionEntrance::getAddress, address)
                .set(
                        AttractionEntrance::getLongitude,
                        req.getLongitude()
                )
                .set(
                        AttractionEntrance::getLatitude,
                        req.getLatitude()
                )
                .set(AttractionEntrance::getCoordSystem, "GCJ02")
                .set(AttractionEntrance::getSupportsWalking, walking)
                .set(AttractionEntrance::getSupportsDriving, driving)
                .set(AttractionEntrance::getPriority, priority)
                .set(AttractionEntrance::getNotice, notice)
                .set(AttractionEntrance::getSourceName, sourceName)
                .set(AttractionEntrance::getSourceUrl, sourceUrl)
                .set(AttractionEntrance::getVerifiedAt, verifiedAt)
                .set(AttractionEntrance::getStatus, status)
                .set(AttractionEntrance::getUpdateTime, now);

        entranceMapper.update(null, update);

        AttractionEntrance saved =
                entranceMapper.selectById(entranceId);

        if (saved == null) {
            throw new IllegalStateException("读取入口保存结果失败");
        }

        return saved;
    }

    private void checkDuplicate(
            Long attractionId,
            Long entranceId,
            String poiId,
            String name,
            String type,
            BigDecimal longitude,
            BigDecimal latitude
    ) {
        if (poiId != null) {
            Long count = entranceMapper.selectCount(
                    new LambdaQueryWrapper<AttractionEntrance>()
                            .eq(
                                    AttractionEntrance::getAttractionId,
                                    attractionId
                            )
                            .eq(AttractionEntrance::getAmapPoiId, poiId)
                            .ne(
                                    entranceId != null,
                                    AttractionEntrance::getId,
                                    entranceId
                            )
            );

            if (count != null && count > 0) {
                throw duplicate();
            }
        }

        Long count = entranceMapper.selectCount(
                new LambdaQueryWrapper<AttractionEntrance>()
                        .eq(
                                AttractionEntrance::getAttractionId,
                                attractionId
                        )
                        .eq(AttractionEntrance::getName, name)
                        .eq(AttractionEntrance::getEntranceType, type)
                        .eq(AttractionEntrance::getLongitude, longitude)
                        .eq(AttractionEntrance::getLatitude, latitude)
                        .ne(
                                entranceId != null,
                                AttractionEntrance::getId,
                                entranceId
                        )
        );

        if (count != null && count > 0) {
            throw duplicate();
        }
    }

    private void validateCoordinates(
            BigDecimal longitude,
            BigDecimal latitude
    ) {
        if (longitude == null || latitude == null) {
            throw new IllegalArgumentException("请填写入口经纬度");
        }

        boolean valid =
                longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                        && longitude.compareTo(BigDecimal.valueOf(180)) <= 0
                        && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
                        && latitude.compareTo(BigDecimal.valueOf(90)) <= 0;

        boolean zeroPair =
                longitude.signum() == 0 && latitude.signum() == 0;

        if (!valid || zeroPair) {
            throw new IllegalArgumentException("入口坐标无效");
        }

        if (longitude.stripTrailingZeros().scale() > 7
                || latitude.stripTrailingZeros().scale() > 7) {
            throw new IllegalArgumentException(
                    "入口经纬度最多保留7位小数"
            );
        }
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
                    fieldName + "不能超过" + maxLength + "个字符"
            );
        }

        return value;
    }

    private String validateUrl(String input) {
        String value = cleanText(input, 4000, "来源链接");

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

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "该入口已存在，请编辑已有入口"
        );
    }

    private ResponseStatusException attractionNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "景点不存在或暂不可编辑"
        );
    }

    private ResponseStatusException entranceNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "入口不存在或不属于当前景点"
        );
    }
}