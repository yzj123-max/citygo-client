package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PostCommentPageResp {

    // 所属帖子ID
    private Long postId;

    // 查询一级评论时为空
    // 查询某条一级评论下的回复时，为该一级评论ID
    private Long rootId;

    // 当前页，从1开始
    private long page;

    // 每页数量
    private long pageSize;

    // 本次查询的评论或回复总数
    private long total;

    // 是否还有下一页
    private boolean hasMore;

    // 当前页的评论或回复
    private List<PostCommentResp> records = new ArrayList<>();
}