package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.AttractionCardResp;
import com.demo.logindemo.dto.BrowseHistoryItemResp;
import com.demo.logindemo.dto.BrowseHistoryPageResp;
import com.demo.logindemo.dto.BrowseHistoryRecordReq;
import com.demo.logindemo.entity.Attraction;
import com.demo.logindemo.entity.UserBrowseHistory;
import com.demo.logindemo.mapper.AttractionMapper;
import com.demo.logindemo.mapper.UserBrowseHistoryMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UserBrowseHistoryService {

    private static final String ATTRACTION = "ATTRACTION";

    private final UserBrowseHistoryMapper historyMapper;
    private final AttractionMapper attractionMapper;
    private final AttractionQueryService attractionQueryService;

    public UserBrowseHistoryService(
            UserBrowseHistoryMapper historyMapper,
            AttractionMapper attractionMapper,
            AttractionQueryService attractionQueryService
    ) {
        this.historyMapper = historyMapper;
        this.attractionMapper = attractionMapper;
        this.attractionQueryService = attractionQueryService;
    }

    /**
     * 记录一次景点浏览。
     * 用户、时间和次数由后端确定。
     */
    @Transactional
    public BrowseHistoryItemResp recordView(
            Long userId,
            BrowseHistoryRecordReq req
    ) {
        checkUserId(userId);

        if (req == null) {
            throw new IllegalArgumentException("请求内容不能为空");
        }

        String targetType = req.getTargetType();

        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("浏览对象类型不能为空");
        }

        targetType = targetType.strip()
                .toUpperCase(Locale.ROOT);

        if (!ATTRACTION.equals(targetType)) {
            throw new IllegalArgumentException(
                    "目前只支持ATTRACTION景点浏览记录"
            );
        }

        Long targetId = req.getTargetId();

        if (targetId == null
                || targetId <= 0
                || targetId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("景点ID无效");
        }

        Attraction attraction =
                attractionMapper.selectById(targetId);

        if (attraction == null
                || !"ACTIVE".equals(attraction.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "景点不存在或暂不可查看"
            );
        }

        /*
         * 新增与重复更新的影响行数可能不同。
         * 这里只判断是否成功，不要求必须等于1。
         */
        int affected = historyMapper.recordView(
                userId,
                ATTRACTION,
                targetId
        );

        if (affected <= 0) {
            throw new IllegalStateException("记录浏览失败");
        }

        UserBrowseHistory record = historyMapper.selectOne(
                ownerQuery(userId)
                        .eq(
                                UserBrowseHistory::getTargetId,
                                targetId
                        )
        );

        if (record == null) {
            throw new IllegalStateException("未能读取浏览记录");
        }

        return buildItems(List.of(record)).get(0);
    }

    /**
     * 分页查询自己的景点浏览记录。
     */
    @Transactional(readOnly = true)
    public BrowseHistoryPageResp getMyHistory(
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

        Long count = historyMapper.selectCount(
                ownerQuery(userId)
        );

        long total = count == null ? 0L : count;

        BrowseHistoryPageResp resp =
                new BrowseHistoryPageResp();

        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(total);
        resp.setHasMore(false);

        if (offset >= total) {
            return resp;
        }

        /*
         * 分页SQL只拼接经过校验的数字。
         * 排序同时使用时间和ID，保证顺序稳定。
         */
        List<UserBrowseHistory> records =
                historyMapper.selectList(
                        ownerQuery(userId)
                                .orderByDesc(
                                        UserBrowseHistory::getLastViewedAt
                                )
                                .orderByDesc(
                                        UserBrowseHistory::getId
                                )
                                .last(
                                        "LIMIT " + pageSize
                                                + " OFFSET " + offset
                                )
                );

        resp.setRecords(buildItems(records));
        resp.setHasMore(
                records.size() < total - offset
        );

        return resp;
    }

    /**
     * 删除自己的一条景点浏览记录。
     * 参数是historyId，不是景点ID。
     */
    @Transactional
    public void deleteOne(
            Long userId,
            Long historyId
    ) {
        checkUserId(userId);

        if (historyId == null || historyId <= 0) {
            throw new IllegalArgumentException("浏览记录ID无效");
        }

        int deleted = historyMapper.delete(
                ownerQuery(userId)
                        .eq(
                                UserBrowseHistory::getId,
                                historyId
                        )
        );

        if (deleted == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "浏览记录不存在或无权删除"
            );
        }
    }

    /**
     * 清空当前用户的景点浏览记录。
     * 不影响其他账号，也不影响收藏。
     */
    @Transactional
    public void clearMyHistory(Long userId) {
        checkUserId(userId);

        historyMapper.delete(
                ownerQuery(userId)
        );
    }

    /**
     * 统一限定当前用户和当前支持的浏览类型。
     */
    private LambdaQueryWrapper<UserBrowseHistory> ownerQuery(
            Long userId
    ) {
        return new LambdaQueryWrapper<UserBrowseHistory>()
                .eq(UserBrowseHistory::getUserId, userId)
                .eq(
                        UserBrowseHistory::getTargetType,
                        ATTRACTION
                );
    }

    /**
     * 批量加载景点卡片，避免逐条查询。
     */
    private List<BrowseHistoryItemResp> buildItems(
            List<UserBrowseHistory> records
    ) {
        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> attractionIds = records.stream()
                .map(UserBrowseHistory::getTargetId)
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

        List<BrowseHistoryItemResp> items =
                new ArrayList<>();

        for (UserBrowseHistory record : records) {
            AttractionCardResp card =
                    cardMap.get(record.getTargetId());

            BrowseHistoryItemResp item =
                    new BrowseHistoryItemResp();

            if (card != null) {
                BeanUtils.copyProperties(card, item);
                item.setAvailable(true);

            } else {
                item.setAttractionId(record.getTargetId());
                item.setName("景点已下架或暂不可用");
                item.setAvailable(false);
                item.setCanNavigate(false);
            }

            item.setHistoryId(record.getId());
            item.setTargetType(record.getTargetType());
            item.setTargetId(record.getTargetId());
            item.setViewCount(record.getViewCount());
            item.setLastViewedAt(record.getLastViewedAt());

            items.add(item);
        }

        return items;
    }

    private void checkUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }
    }
}