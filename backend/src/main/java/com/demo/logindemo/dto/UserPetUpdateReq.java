package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserPetUpdateReq {

    /**
     * 宠物名字。
     */
    private String name;

    /**
     * DOG：狗
     * CAT：猫
     * OTHER：其他
     */
    private String species;

    /**
     * 品种，可不填写。
     */
    private String breed;

    /**
     * 体重，单位：千克。
     */
    private BigDecimal weightKg;

    /**
     * 性格、携宠注意事项等备注，可不填写。
     */
    private String note;

    /**
     * 查询宠物时返回的数据版本。
     * 修改时必须原样传回，用于检查资料是否已被其他操作更新。
     */
    private Long version;
}