package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AttractionCardResp;
import com.demo.logindemo.dto.AttractionCollectItemResp;
import com.demo.logindemo.dto.AttractionCollectPageResp;
import com.demo.logindemo.dto.AttractionCollectResp;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.UserAttractionCollect;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.UserAttractionCollectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserAttractionCollectService {

    private final UserAttractionCollectMapper collectMapper;
    private final AttractionMapper attractionMapper;
    private final AttractionQueryService attractionQueryService;

    public UserAttractionCollectService(
            UserAttractionCollectMapper collectMapper,
            AttractionMapper attractionMapper,
            AttractionQueryService attractionQueryService
    ) {
        this.collectMapper = collectMapper;
        this.attractionMapper = attractionMapper;
        this.attractionQueryService = attractionQueryService;
    }

    /**
     * 收藏景点。
     * 重复收藏不会产生重复记录。
     */
    @Transactional
    public AttractionCollectResp collect(
            Long userId,
            Integer attractionId
    ) {
        checkUserId(userId);
        checkAttractionId(attractionId);

        Attraction attraction =
                attractionMapper.selectById(attractionId);

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "景点不存在或暂不可收藏"
            );
        }

        UserAttractionCollect existing =
                collectMapper.selectOne(
                        ownerQuery(userId, attractionId)
                );

        if (existing != null) {
            return operationResp(attractionId, true);
        }

        UserAttractionCollect record =
                new UserAttractionCollect();

        record.setUserId(userId);
        record.setAttractionId(attractionId);
        record.setCreateTime(new Date());

        try {
            int inserted = collectMapper.insert(record);

            if (inserted != 1) {
                throw new IllegalStateException("收藏失败");
            }

        } catch (DuplicateKeyException e) {
            /*
             * 两个请求同时收藏时，唯一索引会拦截重复插入。
             * 使用锁定查询读取最新已提交的记录。
             */
            UserAttractionCollect concurrentRecord =
                    collectMapper.selectOne(
                            ownerQuery(userId, attractionId)
                                    .last("FOR UPDATE")
                    );

            if (concurrentRecord == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "收藏状态已发生变化，请重试"
                );
            }
        }

        return operationResp(attractionId, true);
    }

    /**
     * 取消收藏。
     * 没有收藏过，也返回未收藏状态。
     * 景点下架后仍然允许取消收藏。
     */
    @Transactional
    public AttractionCollectResp cancel(
            Long userId,
            Integer attractionId
    ) {
        checkUserId(userId);
        checkAttractionId(attractionId);

        collectMapper.delete(
                ownerQuery(userId, attractionId)
        );

        return operationResp(attractionId, false);
    }

    /**
     * 查询自己是否收藏了某个景点。
     */
    @Transactional(readOnly = true)
    public AttractionCollectResp getStatus(
            Long userId,
            Integer attractionId
    ) {
        checkUserId(userId);
        checkAttractionId(attractionId);

        Long count = collectMapper.selectCount(
                ownerQuery(userId, attractionId)
        );

        return operationResp(
                attractionId,
                count != null && count > 0
        );
    }

    /**
     * 分页查询自己的收藏列表。
     * 下架或不存在的景点保留收藏关系，
     * 但不返回其详细资料，available为false。
     */
    @Transactional(readOnly = true)
    public AttractionCollectPageResp getMyCollections(
            Long userId,
            long page,
            long pageSize
    ) {
        checkUserId(userId);

        if (page < 1) {
            throw new IllegalArgumentException(
                    "页码必须从1开始"
            );
        }

        if (pageSize < 1 || pageSize > 50) {
            throw new IllegalArgumentException(
                    "每页数量必须在1到50之间"
            );
        }

        long offset;

        try {
            offset = Math.multiplyExact(
                    page - 1,
                    pageSize
            );
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("页码过大");
        }

        Long count = collectMapper.selectCount(
                new LambdaQueryWrapper<UserAttractionCollect>()
                        .eq(
                                UserAttractionCollect::getUserId,
                                userId
                        )
        );

        long total = count == null ? 0L : count;

        AttractionCollectPageResp resp =
                new AttractionCollectPageResp();

        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(total);
        resp.setHasMore(false);

        if (offset >= total) {
            return resp;
        }

        /*
         * LIMIT和OFFSET仅使用经过校验的数字，
         * 不拼接用户提供的文本。
         */
        List<UserAttractionCollect> records =
                collectMapper.selectList(
                        new LambdaQueryWrapper<UserAttractionCollect>()
                                .eq(
                                        UserAttractionCollect::getUserId,
                                        userId
                                )
                                .orderByDesc(
                                        UserAttractionCollect::getCreateTime
                                )
                                .orderByDesc(
                                        UserAttractionCollect::getId
                                )
                                .last(
                                        "LIMIT " + pageSize
                                                + " OFFSET " + offset
                                )
                );

        List<Long> attractionIds = records.stream()
                .map(record ->
                        record.getAttractionId().longValue()
                )
                .distinct()
                .toList();

        List<AttractionCardResp> cards =
                attractionQueryService.getCardsByIds(
                        attractionIds
                );

        Map<Long, AttractionCardResp> cardMap =
                new HashMap<>();

        for (AttractionCardResp card : cards) {
            cardMap.put(card.getAttractionId(), card);
        }

        List<AttractionCollectItemResp> items =
                new ArrayList<>();

        for (UserAttractionCollect record : records) {
            Long attractionId =
                    record.getAttractionId().longValue();

            AttractionCardResp card =
                    cardMap.get(attractionId);

            AttractionCollectItemResp item =
                    new AttractionCollectItemResp();

            if (card != null) {
                BeanUtils.copyProperties(card, item);
                item.setAvailable(true);

            } else {
                item.setAttractionId(attractionId);
                item.setName("景点已下架或暂不可用");
                item.setAvailable(false);
                item.setCanNavigate(false);
            }

            item.setCollectId(record.getId());
            item.setCollectTime(record.getCreateTime());

            items.add(item);
        }

        resp.setRecords(items);
        resp.setHasMore(
                records.size() < total - offset
        );

        return resp;
    }

    /**
     * 所有收藏操作均限定当前用户。
     */
    private LambdaQueryWrapper<UserAttractionCollect> ownerQuery(
            Long userId,
            Integer attractionId
    ) {
        return new LambdaQueryWrapper<UserAttractionCollect>()
                .eq(UserAttractionCollect::getUserId, userId)
                .eq(
                        UserAttractionCollect::getAttractionId,
                        attractionId
                );
    }

    private void checkUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }
    }

    private void checkAttractionId(Integer attractionId) {
        if (attractionId == null || attractionId <= 0) {
            throw new IllegalArgumentException(
                    "景点ID无效"
            );
        }
    }

    private AttractionCollectResp operationResp(
            Integer attractionId,
            boolean collected
    ) {
        AttractionCollectResp resp =
                new AttractionCollectResp();

        resp.setAttractionId(attractionId);
        resp.setCollected(collected);

        return resp;
    }
}