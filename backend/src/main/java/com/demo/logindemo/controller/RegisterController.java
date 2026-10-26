package com.demo.logindemo.controller;

import com.demo.logindemo.entity.User;
import com.demo.logindemo.service.UserService;
import com.demo.logindemo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RegisterController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    // @RequestBody 接收前端传来的JSON（包含phone、password）
    public Map<String,Object> register(@RequestBody User user){
        Map<String,Object> result = new HashMap<>();
        // 拿到前端传的手机号和密码
        String phone = user.getPhone();
        String password = user.getPassword();

        // 简单非空校验，防止空提交
        if(phone == null || phone.isEmpty() || password == null || password.isEmpty()){
            result.put("msg","手机号和密码不能为空");
            result.put("code",500);
            return result;
        }

        // 判断手机号是否已经注册
        User existUser = userService.getByPhone(phone);
        if(existUser != null){
            result.put("msg","该账号已注册");
            result.put("code",500);
            return result;
        }

        // 保存用户，这里把前端传来的密码存入数据库
        boolean saveOk = userService.addUser(user);

        if(saveOk){
            // 保存成功后查询用户，生成token
            User savedUser = userService.getByPhone(phone);
            String token = JwtUtil.generateToken(savedUser.getId());
            result.put("token", token);
            result.put("code",200);
            result.put("msg","注册成功");
        }else{
            result.put("msg","注册失败");
            result.put("code",500);
        }
        return result;
    }
}
