package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("attraction_detail")
public class AttractionDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 关联 attractions.id
    private Long attractionId;

    // 简短介绍
    private String summary;

    // 详细介绍
    private String description;

    // 特色与看点
    private String highlights;

    // 建议游玩时长
    private String suggestedDuration;

    // 建议游玩季节
    private String bestSeason;

    // 交通出行建议
    private String trafficTips;

    // 游玩注意事项
    private String visitTips;

    // 介绍资料来源名称
    private String sourceName;

    // 介绍资料来源链接
    private String sourceUrl;

    // 人工核实时间，未核实为空
    private Date verifiedAt;

    private Date createTime;

    private Date updateTime;
}