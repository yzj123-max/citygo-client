package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("attraction_entrance")
public class AttractionEntrance {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 关联 attractions.id
    private Long attractionId;

    // 入口名称
    private String name;

    // VISITOR：游客入口
    // VEHICLE：车辆入口
    // PARKING：停车场
    private String entranceType;

    // 对应的高德POI标识
    private String amapPoiId;

    // 入口地址
    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    // 坐标系，高德为GCJ02
    private String coordSystem;

    // 是否适合作为步行导航目的地
    private Boolean supportsWalking;

    // 是否适合作为驾车导航目的地
    private Boolean supportsDriving;

    // 选择优先级，越大越优先
    private Integer priority;

    // 通行限制、停车或步行接驳提示
    private String notice;

    // 来源名称
    private String sourceName;

    // 来源页面
    private String sourceUrl;

    // 人工核实时间
    private Date verifiedAt;

    // ACTIVE：正常；HIDDEN：隐藏；CLOSED：关闭
    private String status;

    private Date createTime;

    private Date updateTime;
}