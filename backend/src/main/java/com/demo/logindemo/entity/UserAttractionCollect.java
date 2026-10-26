package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user_attraction_collect")
public class UserAttractionCollect {

    /**
     * 收藏记录ID，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID，由后端根据登录Token设置。
     */
    private Long userId;

    /**
     * 景点ID，对应attractions.id。
     * 数据库字段是INT，因此使用Integer。
     */
    private Integer attractionId;

    /**
     * 收藏时间。
     */
    private Date createTime;
}
