package com.demo.logindemo.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserRelationPageResp {

    // 当前页，从 1 开始
    private long page;

    // 每页数量
    private long pageSize;

    // 符合条件的用户总数
    private long total;

    // 是否还有下一页
    private boolean hasMore;

    // 用户列表，包含关注、被关注、好友关系
    private List<UserSearchResp> records = new ArrayList<>();
}