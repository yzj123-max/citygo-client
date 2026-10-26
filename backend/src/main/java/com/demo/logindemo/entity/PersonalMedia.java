package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("personal_media")
public class PersonalMedia {

    /**
     * 媒体ID，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID，由后端根据登录Token设置。
     */
    private Long userId;

    /**
     * 图片用途，当前为DIARY。
     */
    private String purpose;

    /**
     * TOS对象名称。
     * 不保存临时签名地址。
     */
    private String objectKey;

    /**
     * 实际图片类型，例如image/jpeg。
     */
    private String contentType;

    /**
     * 文件大小，单位：字节。
     */
    private Long sizeBytes;

    /**
     * 图片宽度，单位：像素。
     */
    private Integer width;

    /**
     * 图片高度，单位：像素。
     */
    private Integer height;

    /**
     * TEMP：临时上传
     * BOUND：已绑定日记
     * DELETE_PENDING：等待清理
     * DELETED：已清理
     */
    private String status;

    /**
     * 临时图片过期时间，绑定日记后清空。
     */
    private Date expiresAt;

    private Date createTime;

    private Date updateTime;
}