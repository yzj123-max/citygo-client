package com.demo.logindemo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class UserPetResp {

    /**
     * 宠物ID。
     */
    private Long petId;

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
     * 品种。
     */
    private String breed;

    /**
     * 体重，单位：千克。
     */
    private BigDecimal weightKg;

    /**
     * 性格、携宠注意事项等备注。
     */
    private String note;

    /**
     * 当前数据版本，修改资料时需要传回。
     */
    private Long version;

    private Date createTime;

    private Date updateTime;
}