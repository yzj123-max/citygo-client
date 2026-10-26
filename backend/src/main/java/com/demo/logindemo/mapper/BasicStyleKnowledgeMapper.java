package com.demo.logindemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.logindemo.entity.BasicStyleKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface BasicStyleKnowledgeMapper extends BaseMapper<BasicStyleKnowledge> {

    @Select("SELECT category, rule_title ruleTitle, rule_content ruleContent, suitable_style suitableStyle FROM basic_style_knowledge")
    List<Map<String,Object>> selectStyleRuleList();
}