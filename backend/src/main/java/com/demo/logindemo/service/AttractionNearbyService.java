package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AmapPoiResp;
import com.demo.logindemo.dto.AttractionSearchPageResp;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.mapper.AttractionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttractionNearbyService {

    private static final double EARTH_RADIUS_METERS =
            6371000.0;

    private final AmapPoiService poiService;
    private final AttractionMapper attractionMapper;

    public AttractionNearbyService(
            AmapPoiService poiService,
            AttractionMapper attractionMapper
    ) {
        this.poiService = poiService;
        this.attractionMapper = attractionMapper;
    }

    public AttractionSearchPageResp nearby(
            BigDecimal longitude,
            BigDecimal latitude,
            String coordSystem,
            int radius,
            int page,
            int pageSize
    ) {
        if (coordSystem == null
                || !"GCJ02".equalsIgnoreCase(coordSystem.trim())) {
            throw new IllegalArgumentException(
                    "附近查询只接受GCJ02高德坐标"
            );
        }

        // 已有高德Service会校验经纬度、半径和分页参数。
        AmapPoiResp response = poiService.nearby(
                longitude,
                latitude,
                radius,
                page,
                pageSize
        );

        List<AmapPoiResp.PoiItem> rawPois =
                response.getPois() == null
                        ? List.of()
                        : response.getPois();

        Map<String, AmapPoiResp.PoiItem> uniquePois =
                new LinkedHashMap<>();

        for (AmapPoiResp.PoiItem poi : rawPois) {
            if (poi == null) {
                continue;
            }

            String poiId = text(poi.getId());
            String name = text(poi.getName());
            String typeCode = text(poi.getTypeCode());

            if (poiId == null
                    || !poiId.matches("[A-Za-z0-9]{1,64}")
                    || name == null
                    || typeCode == null
                    || !typeCode.matches("11\\d{4}")) {
                continue;
            }

            uniquePois.putIfAbsent(poiId, poi);
        }

        Map<String, Attraction> linkedAttractions =
                findLinkedAttractions(
                        new ArrayList<>(uniquePois.keySet())
                );

        List<AttractionSearchPageResp.SearchItem> records =
                new ArrayList<>();

        for (Map.Entry<String, AmapPoiResp.PoiItem> entry :
                uniquePois.entrySet()) {
            String poiId = entry.getKey();
            AmapPoiResp.PoiItem poi = entry.getValue();

            Attraction linked = linkedAttractions.get(poiId);

            if (linked != null
                    && !"ACTIVE".equals(linked.getStatus())) {
                continue;
            }

            BigDecimal[] coordinates =
                    parseCoordinates(poi.getLocation());

            // 无有效坐标就无法计算附近距离。
            if (coordinates == null) {
                continue;
            }

            long distance = distanceMeters(
                    longitude,
                    latitude,
                    coordinates[0],
                    coordinates[1]
            );

            AttractionSearchPageResp.SearchItem item =
                    new AttractionSearchPageResp.SearchItem();

            item.setAmapPoiId(poiId);
            item.setName(poi.getName());
            item.setProvince(poi.getProvinceName());
            item.setCity(poi.getCityName());
            item.setDistrict(poi.getDistrictName());
            item.setAddress(poi.getAddress());
            item.setCategoryCode(poi.getTypeCode());
            item.setCategoryName(poi.getType());
            item.setLongitude(coordinates[0]);
            item.setLatitude(coordinates[1]);
            item.setCoordSystem("GCJ02");
            item.setDistanceMeters(distance);

            AmapPoiResp.BusinessInfo business =
                    poiService.readBusiness(poi);

            item.setPhone(business.getTel());
            item.setCoverUrl(firstPhotoUrl(poi.getPhotos()));

            if (linked != null) {
                item.setAttractionId(linked.getId());
                item.setLinked(true);
            } else {
                item.setAttractionId(null);
                item.setLinked(false);
            }

            records.add(item);
        }

        // 高德已请求按距离排序，这里再按本页计算结果排序。
        records.sort(
                Comparator.comparingLong(
                        AttractionSearchPageResp.SearchItem
                                ::getDistanceMeters
                )
        );

        AttractionSearchPageResp result =
                new AttractionSearchPageResp();

        result.setPage(page);
        result.setPageSize(pageSize);
        result.setReturnedCount(records.size());
        result.setHasMore(
                rawPois.size() >= pageSize && page < 100
        );
        result.setRecords(records);

        return result;
    }

    private Map<String, Attraction> findLinkedAttractions(
            List<String> poiIds
    ) {
        Map<String, Attraction> result = new HashMap<>();

        if (poiIds.isEmpty()) {
            return result;
        }

        List<Attraction> attractions =
                attractionMapper.selectList(
                        new LambdaQueryWrapper<Attraction>()
                                .in(
                                        Attraction::getAmapPoiId,
                                        poiIds
                                )
                );

        for (Attraction attraction : attractions) {
            String poiId = text(attraction.getAmapPoiId());

            if (poiId != null) {
                result.put(poiId, attraction);
            }
        }

        return result;
    }

    private long distanceMeters(
            BigDecimal startLongitude,
            BigDecimal startLatitude,
            BigDecimal endLongitude,
            BigDecimal endLatitude
    ) {
        double lat1 = Math.toRadians(
                startLatitude.doubleValue()
        );

        double lat2 = Math.toRadians(
                endLatitude.doubleValue()
        );

        double deltaLatitude = lat2 - lat1;

        double deltaLongitude = Math.toRadians(
                endLongitude.doubleValue()
                        - startLongitude.doubleValue()
        );

        double sinLatitude =
                Math.sin(deltaLatitude / 2.0);

        double sinLongitude =
                Math.sin(deltaLongitude / 2.0);

        double haversine =
                sinLatitude * sinLatitude
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * sinLongitude
                        * sinLongitude;

        // 避免浮点误差导致asin或sqrt出现非法值。
        haversine = Math.max(
                0.0,
                Math.min(1.0, haversine)
        );

        double distance =
                2.0
                        * EARTH_RADIUS_METERS
                        * Math.asin(Math.sqrt(haversine));

        return Math.round(distance);
    }

    private BigDecimal[] parseCoordinates(String location) {
        String value = text(location);

        if (value == null) {
            return null;
        }

        String[] parts = value.split(",", -1);

        if (parts.length != 2) {
            return null;
        }

        try {
            BigDecimal longitude =
                    new BigDecimal(parts[0].trim());

            BigDecimal latitude =
                    new BigDecimal(parts[1].trim());

            boolean valid =
                    longitude.compareTo(
                            BigDecimal.valueOf(-180)
                    ) >= 0
                            && longitude.compareTo(
                            BigDecimal.valueOf(180)
                    ) <= 0
                            && latitude.compareTo(
                            BigDecimal.valueOf(-90)
                    ) >= 0
                            && latitude.compareTo(
                            BigDecimal.valueOf(90)
                    ) <= 0;

            boolean zeroPair =
                    longitude.signum() == 0
                            && latitude.signum() == 0;

            if (!valid || zeroPair) {
                return null;
            }

            return new BigDecimal[]{longitude, latitude};

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstPhotoUrl(
            List<AmapPoiResp.PhotoItem> photos
    ) {
        if (photos == null) {
            return null;
        }

        for (AmapPoiResp.PhotoItem photo : photos) {
            if (photo == null) {
                continue;
            }

            String url = safeHttpUrl(photo.getUrl());

            if (url != null) {
                return url;
            }
        }

        return null;
    }

    private String safeHttpUrl(String input) {
        String value = text(input);

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
                return null;
            }

            return value;

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String text(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        return input.trim();
    }
}
