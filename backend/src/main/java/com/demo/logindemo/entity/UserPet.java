package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_pet")
public class UserPet {

    /**
     * 宠物ID，数据库自增。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID，由后端根据登录Token设置。
     */
    private Long userId;

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
     * 品种，可为空。
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
     * 数据版本。
     * 后续配置乐观锁插件后生效。
     */
    @Version
    private Long version;

    /**
     * 逻辑删除标记。
     * 0：正常
     * 1：已删除
     */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    private Date createTime;

    private Date updateTime;
}