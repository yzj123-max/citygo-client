package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AttractionNavigationTargetResp;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AttractionNavigationService {

    private static final Set<String> ENTRANCE_TYPES =
            Set.of("VISITOR", "VEHICLE", "PARKING");

    private final AttractionMapper attractionMapper;
    private final AttractionEntranceMapper entranceMapper;

    public AttractionNavigationService(
            AttractionMapper attractionMapper,
            AttractionEntranceMapper entranceMapper
    ) {
        this.attractionMapper = attractionMapper;
        this.entranceMapper = entranceMapper;
    }

    @Transactional(readOnly = true)
    public AttractionNavigationTargetResp getTarget(
            Long attractionId,
            String travelMode
    ) {
        String mode = normalize(travelMode);

        if (!"WALKING".equals(mode) && !"DRIVING".equals(mode)) {
            throw new IllegalArgumentException(
                    "出行方式必须是WALKING或DRIVING"
            );
        }

        if (attractionId == null || attractionId <= 0) {
            throw notFound();
        }

        Attraction attraction =
                attractionMapper.selectById(attractionId);

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw notFound();
        }

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

        Comparator<AttractionEntrance> comparator =
                Comparator.comparingInt(
                                (AttractionEntrance entrance) ->
                                        entrance.getPriority() == null
                                                ? 0
                                                : entrance.getPriority()
                        ).reversed()
                        .thenComparingInt(
                                entrance -> typeRank(
                                        entrance.getEntranceType(),
                                        mode
                                )
                        )
                        .thenComparing(
                                AttractionEntrance::getId,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()
                                )
                        );

        AttractionEntrance selected = entrances.stream()
                .filter(entrance -> isUsable(entrance, mode))
                .min(comparator)
                .orElse(null);

        AttractionNavigationTargetResp resp =
                new AttractionNavigationTargetResp();

        resp.setAttractionId(attraction.getId());
        resp.setAttractionName(attraction.getAttrName());
        resp.setTravelMode(mode);
        resp.setCoordSystem("GCJ02");

        if (selected != null) {
            resp.setEntranceId(selected.getId());
            resp.setDestinationName(selected.getName());
            resp.setDestinationType(
                    normalize(selected.getEntranceType())
            );
            resp.setAddress(selected.getAddress());
            resp.setLongitude(selected.getLongitude());
            resp.setLatitude(selected.getLatitude());
            resp.setAmapPoiId(selected.getAmapPoiId());
            resp.setFallback(false);
            resp.setNotice(selected.getNotice());
            resp.setSourceName(selected.getSourceName());
            resp.setSourceUrl(
                    safeHttpUrl(selected.getSourceUrl())
            );
            resp.setVerifiedAt(selected.getVerifiedAt());

            return resp;
        }

        // 没有适合当前出行方式的入口，尝试景点基础坐标。
        if (!validCoordinates(
                attraction.getLongitude(),
                attraction.getLatitude(),
                attraction.getCoordSystem()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "该景点暂缺可用导航坐标，请稍后重试或在地图中搜索"
            );
        }

        resp.setDestinationName(attraction.getAttrName());
        resp.setDestinationType("ATTRACTION");
        resp.setAddress(attraction.getAddress());
        resp.setLongitude(attraction.getLongitude());
        resp.setLatitude(attraction.getLatitude());
        resp.setAmapPoiId(attraction.getAmapPoiId());
        resp.setFallback(true);

        if ("DRIVING".equals(mode)) {
            resp.setNotice(
                    "暂无适合驾车的入口或停车场坐标，"
                            + "当前使用景点位置，请确认车辆可达位置"
            );
        } else {
            resp.setNotice(
                    "暂无适合步行的入口坐标，"
                            + "当前使用景点位置，请确认实际游客入口"
            );
        }

        // 原表source是介绍资料来源，不能当作坐标来源。
        resp.setSourceName(null);
        resp.setSourceUrl(null);
        resp.setVerifiedAt(null);

        return resp;
    }

    private boolean isUsable(
            AttractionEntrance entrance,
            String mode
    ) {
        if (!ENTRANCE_TYPES.contains(
                normalize(entrance.getEntranceType())
        )) {
            return false;
        }

        if (entrance.getName() == null
                || entrance.getName().isBlank()) {
            return false;
        }

        boolean supportsMode = "WALKING".equals(mode)
                ? Boolean.TRUE.equals(
                entrance.getSupportsWalking()
        )
                : Boolean.TRUE.equals(
                entrance.getSupportsDriving()
        );

        return supportsMode && validCoordinates(
                entrance.getLongitude(),
                entrance.getLatitude(),
                entrance.getCoordSystem()
        );
    }

    // 人工优先级相同时，再按入口类型选择。
    private int typeRank(String entranceType, String mode) {
        String type = normalize(entranceType);

        if ("DRIVING".equals(mode)) {
            return switch (type) {
                case "PARKING" -> 0;
                case "VEHICLE" -> 1;
                default -> 2;
            };
        }

        return switch (type) {
            case "VISITOR" -> 0;
            case "VEHICLE" -> 1;
            default -> 2;
        };
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
                longitude.signum() == 0
                        && latitude.signum() == 0;

        return inRange && !zeroPair;
    }

    private String safeHttpUrl(String input) {
        if (input == null || input.isBlank()) {
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