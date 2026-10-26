package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("restaurants")
public class Restaurant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String restName;
    private String city;
    private String district;
    private String foodType;
    private String avgPrice;
    private String recommend;
    private String petFriendly;
}