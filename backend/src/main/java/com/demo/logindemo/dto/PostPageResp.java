package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PostPageResp {

    // 当前页，从1开始
    private long page;

    // 每页数量
    private long pageSize;

    // 当前用户有权查看的帖子总数
    private long total;

    // 是否还有下一页
    private boolean hasMore;

    // 本页帖子，复用已有的帖子详情返回格式
    private List<PostDetailResp> records = new ArrayList<>();
}
