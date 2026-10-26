package com.demo.logindemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.logindemo.entity.Attraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param; // 新增导入

@Mapper
public interface AttractionMapper extends BaseMapper<Attraction> {
    // 新增：根据景点id，查询ootd_desc
    String getOotdDescById(@Param("id") Long id);
}