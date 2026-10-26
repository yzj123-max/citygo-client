package com.demo.logindemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.logindemo.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}