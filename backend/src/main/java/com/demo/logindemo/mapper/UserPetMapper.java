package com.demo.logindemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.logindemo.entity.UserPet;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPetMapper extends BaseMapper<UserPet> {
}