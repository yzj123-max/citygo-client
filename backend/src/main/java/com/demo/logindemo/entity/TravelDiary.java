package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("travel_diary")
public class TravelDiary {

    /**
     * 日记ID，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID，由后端根据登录Token设置。
     */
    private Long userId;

    /**
     * 日记标题。
     */
    private String title;

    /**
     * 日记正文。
     */
    private String content;

    /**
     * 旅行城市或目的地，可为空。
     */
    private String cityName;

    /**
     * 旅行日期，只包含年月日。
     */
    private LocalDate travelDate;

    /**
     * 封面图片的媒体ID，可为空。
     */
    private Long coverMediaId;

    /**
     * 数据版本，用于检查并发修改。
     */
    @Version
    private Long version;

    /**
     * 0：正常
     * 1：已删除
     */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    private Date createTime;

    private Date updateTime;
}