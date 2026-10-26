package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("travel_diary_media")
public class TravelDiaryMedia {

    /**
     * 关联记录ID，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属日记ID。
     */
    private Long diaryId;

    /**
     * 图片媒体ID，对应personal_media.id。
     */
    private Long mediaId;

    /**
     * 图片展示顺序，从0开始。
     */
    private Integer sortOrder;

    /**
     * 建立关联的时间。
     */
    private Date createTime;
}