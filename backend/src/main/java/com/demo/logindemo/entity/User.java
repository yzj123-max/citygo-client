package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String password;

    private String nickname;
    private String avatarUrl;
    private String bgImageUrl;
    @TableField("`desc`")
    private String desc;
    private String gender;
    private Date birthday;
    private String region;
    private String job;
    private String school;

    private Date createTime;
    private Date updateTime;

    @TableField("verify_code")
    private String verifyCode;

    // 已经删掉 @TableField(exist = false)！！
    private Long codeExpireTime;
}