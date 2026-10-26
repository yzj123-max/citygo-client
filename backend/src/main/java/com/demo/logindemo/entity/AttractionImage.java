package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("attraction_image")
public class AttractionImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 关联 attractions.id
    private Long attractionId;

    // EXTERNAL：外部图片
    // TOS：自有桶图片
    private String storageType;

    // 外部图片地址
    private String imageUrl;

    // TOS对象名称，不保存临时签名地址
    private String objectKey;

    // 图片说明
    private String caption;

    // 是否封面
    private Boolean isCover;

    // 展示顺序，越小越靠前
    private Integer sortOrder;

    // 图片来源名称
    private String sourceName;

    // 图片来源页面
    private String sourceUrl;

    // ACTIVE：正常；HIDDEN：隐藏
    private String status;

    private Date createTime;

    private Date updateTime;
}