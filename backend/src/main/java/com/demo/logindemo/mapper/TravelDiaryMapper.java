package com.demo.logindemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.logindemo.entity.TravelDiary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TravelDiaryMapper
        extends BaseMapper<TravelDiary> {
}