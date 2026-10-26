package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AmapPoiResp;
import com.demo.logindemo.dto.AttractionPoiResolveReq;
import com.demo.logindemo.dto.AttractionPoiResolveResp;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.AttractionImage;
import com.demo.logindemo.mapper.AttractionImageMapper;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class AttractionPoiResolveService {

    private final AttractionMapper attractionMapper;
    private final AttractionImageMapper imageMapper;
    private final UserMapper userMapper;
    private final AmapPoiService poiService;
    private final TransactionTemplate transactionTemplate;

    public AttractionPoiResolveService(
            AttractionMapper attractionMapper,
            AttractionImageMapper imageMapper,
            UserMapper userMapper,
            AmapPoiService poiService,
            PlatformTransactionManager transactionManager
    ) {
        this.attractionMapper = attractionMapper;
        this.imageMapper = imageMapper;
        this.userMapper = userMapper;
        this.poiService = poiService;

        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        this.transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );
    }

    public AttractionPoiResolveResp resolve(
            Long currentUserId,
            AttractionPoiResolveReq req
    ) {
        requireUser(currentUserId);

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        String poiId = text(req.getAmapPoiId());

        if (poiId == null
                || !poiId.matches("[A-Za-z0-9]{1,64}")) {
            throw new IllegalArgumentException(
                    "请输入有效的高德POI ID"
            );
        }

        Attraction existing = findByPoiId(poiId);

        if (existing != null) {
            return existingResult(existing);
        }

        // 仅使用后端向高德重新查询的数据。
        // 此时尚未开启写事务，网络等待不占用数据库锁。
        AmapPoiResp response = poiService.detail(poiId);

        AmapPoiResp.PoiItem poi = response.getPois().stream()
                .filter(Objects::nonNull)
                .filter(item -> poiId.equals(text(item.getId())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "高德未返回对应景点"
                ));

        String typeCode = text(poi.getTypeCode());

        if (typeCode == null
                || !typeCode.matches("11\\d{4}")) {
            throw new IllegalArgumentException(
                    "当前POI不属于风景名胜分类"
            );
        }

        String name = text(poi.getName());

        if (name == null || name.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "高德返回的景点名称无效"
            );
        }

        AmapPoiResp.BusinessInfo business =
                poiService.readBusiness(poi);

        try {
            AttractionPoiResolveResp result =
                    transactionTemplate.execute(
                            transactionStatus -> createOrReuse(
                                    poiId,
                                    poi,
                                    business
                            )
                    );

            if (result == null) {
                throw new IllegalStateException("景点处理结果为空");
            }

            return result;

        } catch (DuplicateKeyException e) {
            // 两个用户同时点击同一POI时，唯一索引保证只创建一条。
            // 在失败事务回滚之后重新读取已提交的记录。
            Attraction concurrent = findByPoiId(poiId);

            if (concurrent != null) {
                return existingResult(concurrent);
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "景点记录存在唯一约束冲突，请联系管理员确认"
            );
        }
    }

    private AttractionPoiResolveResp createOrReuse(
            String poiId,
            AmapPoiResp.PoiItem poi,
            AmapPoiResp.BusinessInfo business
    ) {
        // 请求高德期间可能已有其他请求创建了记录。
        Attraction existing = findByPoiId(poiId);

        if (existing != null) {
            return existingResult(existing);
        }

        Date now = new Date();

        Attraction attraction = new Attraction();

        attraction.setAttrName(poi.getName().trim());

        // 原表province、city、level为非空字段。
        // 缺失时使用空文本，不编造地区或景区等级。
        attraction.setProvince(
                requiredDatabaseText(
                        poi.getProvinceName(),
                        100
                )
        );

        attraction.setCity(
                requiredDatabaseText(
                        poi.getCityName(),
                        100
                )
        );

        attraction.setLevel("");

        attraction.setDistrict(
                optionalText(poi.getDistrictName(), 255)
        );

        attraction.setAddress(
                optionalText(poi.getAddress(), 512)
        );

        attraction.setAmapPoiId(poiId);
        attraction.setAdcode(validAdcode(poi.getAdcode()));
        attraction.setCategoryCode(poi.getTypeCode());

        attraction.setCategoryName(
                optionalText(poi.getType(), 100)
        );

        attraction.setPhone(
                optionalText(business.getTel(), 255)
        );

        BigDecimal[] coordinates =
                parseCoordinates(poi.getLocation());

        if (coordinates != null) {
            attraction.setLongitude(coordinates[0]);
            attraction.setLatitude(coordinates[1]);
        }

        attraction.setCoordSystem("GCJ02");
        attraction.setStatus("ACTIVE");
        attraction.setSortWeight(0);
        attraction.setPoiSyncedAt(now);

        // 不设置feature、票价、开放时间、宠物政策、
        // 人工核实时间或OOTD描述，避免把缺失信息当作事实。
        if (attractionMapper.insert(attraction) != 1
                || attraction.getId() == null) {
            throw new IllegalStateException("创建景点失败");
        }

        addImages(
                attraction.getId(),
                poi,
                now
        );

        return buildResult(attraction, true);
    }

    private void addImages(
            Long attractionId,
            AmapPoiResp.PoiItem poi,
            Date now
    ) {
        if (poi.getPhotos() == null) {
            return;
        }

        Set<String> urls = new LinkedHashSet<>();

        for (AmapPoiResp.PhotoItem photo : poi.getPhotos()) {
            if (photo == null) {
                continue;
            }

            String url = safeHttpUrl(photo.getUrl());

            if (url != null) {
                urls.add(url);
            }

            if (urls.size() >= 10) {
                break;
            }
        }

        int order = 0;

        for (String url : urls) {
            AttractionImage image = new AttractionImage();
            image.setAttractionId(attractionId);
            image.setStorageType("EXTERNAL");
            image.setImageUrl(url);
            image.setIsCover(order == 0);
            image.setSortOrder(order);
            image.setSourceName("高德POI");
            image.setStatus("ACTIVE");
            image.setCreateTime(now);
            image.setUpdateTime(now);

            if (imageMapper.insert(image) != 1) {
                throw new IllegalStateException("保存景点图片失败");
            }

            order++;
        }
    }

    private Attraction findByPoiId(String poiId) {
        return attractionMapper.selectOne(
                new LambdaQueryWrapper<Attraction>()
                        .eq(Attraction::getAmapPoiId, poiId)
        );
    }

    private AttractionPoiResolveResp existingResult(
            Attraction attraction
    ) {
        // 已隐藏或停用的记录不允许通过resolve重新启用。
        if (!"ACTIVE".equals(attraction.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "景点不存在或暂不可查看"
            );
        }

        return buildResult(attraction, false);
    }

    private AttractionPoiResolveResp buildResult(
            Attraction attraction,
            boolean created
    ) {
        AttractionPoiResolveResp result =
                new AttractionPoiResolveResp();

        result.setAttractionId(attraction.getId());
        result.setAttractionName(attraction.getAttrName());
        result.setAmapPoiId(attraction.getAmapPoiId());
        result.setCreated(created);

        return result;
    }

    private void requireUser(Long currentUserId) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }
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

            if (!valid
                    || zeroPair
                    || longitude.stripTrailingZeros().scale() > 7
                    || latitude.stripTrailingZeros().scale() > 7) {
                return null;
            }

            return new BigDecimal[]{longitude, latitude};

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String requiredDatabaseText(
            String input,
            int maxLength
    ) {
        String value = optionalText(input, maxLength);
        return value == null ? "" : value;
    }

    private String optionalText(String input, int maxLength) {
        String value = text(input);

        if (value != null && value.length() > maxLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "高德返回字段超出数据库允许长度"
            );
        }

        return value;
    }

    private String validAdcode(String input) {
        String value = text(input);

        return value != null && value.matches("\\d{6}")
                ? value
                : null;
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

    private String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}