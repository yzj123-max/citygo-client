package com.demo.logindemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    // 根据手机号查询用户
    public User getByPhone(String phone){
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        return userMapper.selectOne(wrapper);
    }

    // 新增用户
    public boolean addUser(User user){
        return userMapper.insert(user) > 0;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    public boolean updateById(User user) {
        return userMapper.updateById(user) > 0;
    }

    // ===== 新增：按手机号更新验证码（重点！）=====
    public boolean updateVerifyCode(String phone, String verifyCode){
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getPhone, phone)
                .set(User::getVerifyCode, verifyCode);
        int row = userMapper.update(null, updateWrapper);
        System.out.println("更新影响行数 = " + row);
        return row > 0;
    }
}