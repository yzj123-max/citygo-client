package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.dto.AmapPoiResp;
import com.demo.logindemo.dto.AttractionPoiSyncResp;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.AttractionImage;
import com.demo.logindemo.mapper.AttractionImageMapper;
import com.demo.logindemo.mapper.AttractionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Service
public class AttractionPoiSyncService {

    private final AttractionMapper attractionMapper;
    private final AttractionImageMapper imageMapper;
    private final AmapPoiService poiService;
    private final TransactionTemplate transactionTemplate;

    public AttractionPoiSyncService(
            AttractionMapper attractionMapper,
            AttractionImageMapper imageMapper,
            AmapPoiService poiService,
            PlatformTransactionManager transactionManager
    ) {
        this.attractionMapper = attractionMapper;
        this.imageMapper = imageMapper;
        this.poiService = poiService;

        this.transactionTemplate =
                new TransactionTemplate(transactionManager);

        this.transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );
    }

    public AttractionPoiSyncResp sync(Long attractionId) {
        Attraction before = requireAttraction(attractionId);

        String poiId = text(before.getAmapPoiId());

        if (poiId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该景点尚未关联高德POI，请先确认匹配关系"
            );
        }

        // 先请求高德，再开启数据库写事务。
        // 网络等待期间不锁定景点记录。
        AmapPoiResp response = poiService.detail(poiId);

        AmapPoiResp.PoiItem poi = response.getPois().stream()
                .filter(Objects::nonNull)
                .filter(item -> poiId.equals(text(item.getId())))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "高德未返回已关联的景点，请检查POI是否仍有效"
                ));

        AmapPoiResp.BusinessInfo business =
                poiService.readBusiness(poi);

        AttractionPoiSyncResp result = transactionTemplate.execute(
                transactionStatus -> applySync(
                        attractionId,
                        poiId,
                        poi,
                        business
                )
        );

        if (result == null) {
            throw new IllegalStateException("同步结果为空");
        }

        return result;
    }

    private AttractionPoiSyncResp applySync(
            Long attractionId,
            String requestedPoiId,
            AmapPoiResp.PoiItem poi,
            AmapPoiResp.BusinessInfo business
    ) {
        Attraction attraction = attractionMapper.selectOne(
                new LambdaQueryWrapper<Attraction>()
                        .eq(Attraction::getId, attractionId)
                        .last("FOR UPDATE")
        );

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw notFound();
        }

        // 请求高德期间，如果关联关系被修改，则停止写入。
        if (!requestedPoiId.equals(text(attraction.getAmapPoiId()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "景点的高德关联已变化，请重新同步"
            );
        }

        String incomingAdcode = validAdcode(poi.getAdcode());
        String existingAdcode = text(attraction.getAdcode());

        // 行政区发生变化时交由人工确认，避免关联错误被继续覆盖。
        if (existingAdcode != null
                && incomingAdcode != null
                && !existingAdcode.equals(incomingAdcode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "高德返回的行政区与现有记录不一致，请人工确认关联"
            );
        }

        List<String> updatedFields = new ArrayList<>();

        LambdaUpdateWrapper<Attraction> update =
                new LambdaUpdateWrapper<>();

        update.eq(Attraction::getId, attractionId)
                .eq(Attraction::getStatus, "ACTIVE")
                .eq(Attraction::getAmapPoiId, requestedPoiId);

        addTextUpdate(
                update,
                Attraction::getAddress,
                attraction.getAddress(),
                poi.getAddress(),
                512,
                "address",
                updatedFields
        );

        addTextUpdate(
                update,
                Attraction::getDistrict,
                attraction.getDistrict(),
                poi.getDistrictName(),
                255,
                "district",
                updatedFields
        );

        addTextUpdate(
                update,
                Attraction::getAdcode,
                attraction.getAdcode(),
                incomingAdcode,
                6,
                "adcode",
                updatedFields
        );

        addTextUpdate(
                update,
                Attraction::getCategoryCode,
                attraction.getCategoryCode(),
                poi.getTypeCode(),
                20,
                "categoryCode",
                updatedFields
        );

        addTextUpdate(
                update,
                Attraction::getCategoryName,
                attraction.getCategoryName(),
                poi.getType(),
                100,
                "categoryName",
                updatedFields
        );

        addTextUpdate(
                update,
                Attraction::getPhone,
                attraction.getPhone(),
                business.getTel(),
                255,
                "phone",
                updatedFields
        );

        BigDecimal[] coordinates =
                parseCoordinates(poi.getLocation());

        if (coordinates != null) {
            boolean longitudeChanged = !sameNumber(
                    attraction.getLongitude(),
                    coordinates[0]
            );

            boolean latitudeChanged = !sameNumber(
                    attraction.getLatitude(),
                    coordinates[1]
            );

            if (longitudeChanged) {
                update.set(
                        Attraction::getLongitude,
                        coordinates[0]
                );
                updatedFields.add("longitude");
            }

            if (latitudeChanged) {
                update.set(
                        Attraction::getLatitude,
                        coordinates[1]
                );
                updatedFields.add("latitude");
            }

            if (!"GCJ02".equals(attraction.getCoordSystem())) {
                update.set(Attraction::getCoordSystem, "GCJ02");
                updatedFields.add("coordSystem");
            }
        }

        Date now = new Date();
        update.set(Attraction::getPoiSyncedAt, now);

        // 不依赖受影响行数判断内容是否变化：
        // 重复同步可能恰好发生在数据库时间精度相同的一秒内。
        attractionMapper.update(null, update);

        int imagesAdded = addImages(
                attractionId,
                poi.getPhotos(),
                now
        );

        AttractionPoiSyncResp result =
                new AttractionPoiSyncResp();

        result.setAttractionId(attractionId);
        result.setAttractionName(attraction.getAttrName());
        result.setAmapPoiId(requestedPoiId);
        result.setUpdatedFields(updatedFields);
        result.setImagesAddedCount(imagesAdded);
        result.setSyncedAt(now);

        return result;
    }

    private void addTextUpdate(
            LambdaUpdateWrapper<Attraction> update,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<
                    Attraction, ?> column,
            String existing,
            String incoming,
            int maxLength,
            String fieldName,
            List<String> updatedFields
    ) {
        String value = text(incoming);

        // 缺失或异常长度的数据不覆盖已有资料。
        if (value == null || value.length() > maxLength) {
            return;
        }

        if (!Objects.equals(text(existing), value)) {
            update.set(column, value);
            updatedFields.add(fieldName);
        }
    }

    private int addImages(
            Long attractionId,
            List<AmapPoiResp.PhotoItem> photos,
            Date now
    ) {
        if (photos == null || photos.isEmpty()) {
            return 0;
        }

        List<AttractionImage> existingImages =
                imageMapper.selectList(
                        new LambdaQueryWrapper<AttractionImage>()
                                .eq(
                                        AttractionImage::getAttractionId,
                                        attractionId
                                )
                );

        Set<String> existingUrls = new HashSet<>();
        boolean hasCover = false;
        int nextSortOrder = 0;

        for (AttractionImage image : existingImages) {
            if ("EXTERNAL".equals(image.getStorageType())) {
                String url = text(image.getImageUrl());

                if (url != null) {
                    existingUrls.add(url);
                }
            }

            if ("ACTIVE".equals(image.getStatus())
                    && Boolean.TRUE.equals(image.getIsCover())) {
                hasCover = true;
            }

            if (image.getSortOrder() != null) {
                nextSortOrder = Math.max(
                        nextSortOrder,
                        image.getSortOrder()
                );
            }
        }

        Set<String> incomingUrls = new LinkedHashSet<>();

        for (AmapPoiResp.PhotoItem photo : photos) {
            if (photo == null) {
                continue;
            }

            String url = safeHttpUrl(photo.getUrl());

            if (url != null) {
                incomingUrls.add(url);
            }

            // 每次最多处理10张来源图片。
            if (incomingUrls.size() >= 10) {
                break;
            }
        }

        int added = 0;

        for (String url : incomingUrls) {
            // 包括已隐藏图片，避免同步时重新添加它。
            if (existingUrls.contains(url)) {
                continue;
            }

            if (nextSortOrder == Integer.MAX_VALUE) {
                throw new IllegalStateException("景点图片排序值异常");
            }

            nextSortOrder++;

            AttractionImage image = new AttractionImage();
            image.setAttractionId(attractionId);
            image.setStorageType("EXTERNAL");
            image.setImageUrl(url);
            image.setCaption(null);
            image.setIsCover(!hasCover);
            image.setSortOrder(nextSortOrder);
            image.setSourceName("高德POI");
            image.setStatus("ACTIVE");
            image.setCreateTime(now);
            image.setUpdateTime(now);

            if (imageMapper.insert(image) != 1) {
                throw new IllegalStateException("保存景点图片失败");
            }

            hasCover = true;
            existingUrls.add(url);
            added++;
        }

        return added;
    }

    private Attraction requireAttraction(Long attractionId) {
        if (attractionId == null || attractionId <= 0) {
            throw notFound();
        }

        Attraction attraction =
                attractionMapper.selectById(attractionId);

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw notFound();
        }

        return attraction;
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

            // 数据库坐标最多保留7位小数。
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

    private boolean sameNumber(
            BigDecimal first,
            BigDecimal second
    ) {
        return first != null
                && second != null
                && first.compareTo(second) == 0;
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

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "景点不存在或暂不可同步"
        );
    }
}