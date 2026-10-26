package com.demo.logindemo.service;

import com.demo.logindemo.dto.PostDetailResp;
import com.demo.logindemo.dto.PostPageResp;
import com.demo.logindemo.mapper.PostFeedMapper;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class PostFeedService {

    private final PostFeedMapper feedMapper;
    private final UserMapper userMapper;
    private final PostDetailService detailService;

    public PostFeedService(
            PostFeedMapper feedMapper,
            UserMapper userMapper,
            PostDetailService detailService
    ) {
        this.feedMapper = feedMapper;
        this.userMapper = userMapper;
        this.detailService = detailService;
    }

    @Transactional(readOnly = true)
    public PostPageResp getFeed(
            Long currentUserId,
            long page,
            long pageSize
    ) {
        if (currentUserId == null
                || currentUserId <= 0
                || userMapper.selectById(currentUserId) == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录用户不存在，请重新登录"
            );
        }

        if (page < 1) {
            throw new IllegalArgumentException("页码必须从1开始");
        }

        if (pageSize < 1 || pageSize > 50) {
            throw new IllegalArgumentException("每页数量必须在1到50之间");
        }

        long offset;
        try {
            offset = Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("页码过大");
        }

        long total = feedMapper.countVisible(currentUserId);

        List<PostDetailResp> records = new ArrayList<>();

        if (offset < total) {
            List<Long> postIds = feedMapper.selectVisibleIds(
                    currentUserId,
                    offset,
                    pageSize
            );

            for (Long postId : postIds) {
                records.add(
                        detailService.getDetail(postId, currentUserId)
                );
            }
        }

        PostPageResp resp = new PostPageResp();
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setTotal(total);
        resp.setRecords(records);
        resp.setHasMore(
                offset < total
                        && records.size() < total - offset
        );

        return resp;
    }
}