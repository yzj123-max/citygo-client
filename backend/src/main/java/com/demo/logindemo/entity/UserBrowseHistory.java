package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_browse_history")
public class UserBrowseHistory {

    /**
     * 浏览记录ID，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID，由后端根据登录Token设置。
     */
    private Long userId;

    /**
     * 浏览对象类型。
     * ATTRACTION：景点
     * POST：帖子，暂时预留
     */
    private String targetType;

    /**
     * 被浏览对象的ID。
     */
    private Long targetId;

    /**
     * 浏览次数。
     */
    private Long viewCount;

    /**
     * 最近浏览时间。
     */
    private Date lastViewedAt;

    /**
     * 首次创建记录的时间。
     */
    private Date createTime;
}