package com.demo.logindemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
//import java.time.LocalDateTime;

@Data
@TableName("basic_style_knowledge")
public class BasicStyleKnowledge {
    @TableId(type = IdType.AUTO)
    private Integer id;
    // 分类：色彩搭配 / 版型搭配 / 风格搭配
    private String category;
    // 规则标题
    private String ruleTitle;
    // 详细搭配规则
    private String ruleContent;
    // 适配风格
    private String suitableStyle;
    // 创建时间，临时注释排查问题
    //private LocalDateTime createTime;
}