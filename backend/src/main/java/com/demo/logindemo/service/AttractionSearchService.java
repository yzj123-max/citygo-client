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
public class AttractionSearchService {

    private final AmapPoiService poiService;
    private final AttractionMapper attractionMapper;

    public AttractionSearchService(
            AmapPoiService poiService,
            AttractionMapper attractionMapper
    ) {
        this.poiService = poiService;
        this.attractionMapper = attractionMapper;
    }

    public AttractionSearchPageResp search(
            String keyword,
            String city,
            String categoryCode,
            int page,
            int pageSize
    ) {
        String searchKeyword = cleanText(
                keyword,
                80,
                "搜索关键词"
        );

        if (searchKeyword == null) {
            throw new IllegalArgumentException("请输入景点名称");
        }

        String searchCity = cleanText(city, 100, "城市");

        String category = cleanText(
                categoryCode,
                6,
                "景点分类"
        );

        if (category == null) {
            category = "110000";
        }

        if (!category.matches("11\\d{4}")) {
            throw new IllegalArgumentException(
                    "请选择有效的风景名胜分类编码"
            );
        }

        // 不传城市时全国搜索；传城市时限制在该城市。
        AmapPoiResp response = poiService.search(
                searchKeyword,
                searchCity,
                category,
                searchCity != null,
                page,
                pageSize
        );

        List<AmapPoiResp.PoiItem> rawPois =
                response.getPois() == null
                        ? List.of()
                        : response.getPois();

        // 按高德POI ID去重。
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

            // 已被本地隐藏或停用的景点，不通过高德搜索重新展示。
            if (linked != null
                    && !"ACTIVE".equals(linked.getStatus())) {
                continue;
            }

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
            item.setCoordSystem("GCJ02");

            BigDecimal[] coordinates =
                    parseCoordinates(poi.getLocation());

            if (coordinates != null) {
                item.setLongitude(coordinates[0]);
                item.setLatitude(coordinates[1]);
            }

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

        // 仅优化当前页展示顺序，不改变数据库关联关系。
        records.sort(
                Comparator.comparingInt(
                        (AttractionSearchPageResp.SearchItem item) ->
                                nameRank(
                                        item.getName(),
                                        searchKeyword
                                )
                )
        );

        AttractionSearchPageResp result =
                new AttractionSearchPageResp();

        result.setPage(page);
        result.setPageSize(pageSize);
        result.setReturnedCount(records.size());

        // 使用高德原始返回条数判断是否可尝试下一页，
        // 不将count当作全国搜索总数。
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

    private int nameRank(String name, String keyword) {
        String value = text(name);

        if (keyword.equals(value)) {
            return 0;
        }

        if (value != null && value.contains(keyword)) {
            return 1;
        }

        return 2;
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

    private String cleanText(
            String input,
            int maxLength,
            String fieldName
    ) {
        String value = text(input);

        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "不能超过" + maxLength + "个字符"
            );
        }

        return value;
    }

    private String text(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        return input.trim();
    }
}