package com.demo.logindemo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.logindemo.dto.UpdateUserInfoDTO;
import com.demo.logindemo.dto.UserInfoDTO;
import com.demo.logindemo.dto.UserLoginDTO;
import com.demo.logindemo.dto.UserRegisterDTO;
import com.demo.logindemo.entity.Result;
import com.demo.logindemo.entity.User;
import com.demo.logindemo.mapper.UserMapper;
import com.demo.logindemo.service.UserService;
import com.demo.logindemo.util.JwtUtil;
import com.demo.logindemo.util.PasswordUtil;
import com.demo.logindemo.vo.LoginVO;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取验证码 POST /sendCode
     */
    @PostMapping("/sendCode")
    public Result<?> sendCode(@RequestBody UserLoginDTO dto) {
        String phone = dto.getPhone();

        if (phone == null || phone.isBlank()) {
            return Result.fail("手机号不能为空");
        }

        User user = userService.getByPhone(phone);

        if (user == null) {
            return Result.fail("手机号未注册，请先注册");
        }

        // 生成6位验证码，5分钟过期
        String code = String.format(
                "%06d",
                new Random().nextInt(1000000)
        );

        long expireTime =
                System.currentTimeMillis() + 5 * 60 * 1000;

        System.out.println("======================");
        System.out.println("手机号：" + phone);
        System.out.println("本次生成验证码：【" + code + "】");
        System.out.println("======================");

        user.setVerifyCode(code);
        user.setCodeExpireTime(expireTime);
        userService.updateById(user);

        // 保留当前调试流程
        return Result.success(code);
    }

    /**
     * 验证码登录 POST /userLogin/code
     */
    @PostMapping("/userLogin/code")
    public Result<?> loginByCode(@RequestBody UserLoginDTO dto) {
        String phone = dto.getPhone();
        String inputCode = dto.getVerifyCode();

        if (phone == null || phone.isBlank()
                || inputCode == null || inputCode.isBlank()) {
            return Result.fail("手机号和验证码不能为空");
        }

        LambdaQueryWrapper<User> queryWrapper =
                new LambdaQueryWrapper<>();

        queryWrapper.eq(User::getPhone, phone);

        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return Result.fail("手机号不存在");
        }

        System.out.println(
                "【DEBUG】查询出来的user id = " + user.getId()
        );

        System.out.println(
                "【登录校验】数据库读取保存验证码："
                        + user.getVerifyCode()
        );

        System.out.println(
                "【登录校验】前端传入验证码：" + inputCode
        );

        if (user.getVerifyCode() == null
                || !user.getVerifyCode().equals(inputCode)) {
            return Result.fail("验证码错误");
        }

        if (System.currentTimeMillis() > user.getCodeExpireTime()) {
            return Result.fail("验证码已过期，请重新获取");
        }

        // 登录成功后清空验证码
        user.setVerifyCode(null);
        user.setCodeExpireTime(null);
        userService.updateById(user);

        if (user.getId() == null) {
            return Result.fail("用户ID异常");
        }

        String token = JwtUtil.generateToken(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);

        return Result.success(vo);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<?> logout() {
        return Result.success("退出成功，请前端清除本地token");
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    public Result<?> getUserInfo(
            @RequestHeader("Authorization") String tokenHeader
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        Claims claims = JwtUtil.parseToken(token);
        Long userId = Long.valueOf(claims.getSubject());

        User user = userService.getById(userId);

        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(user.getId());
        dto.setPhone(user.getPhone());
        dto.setNickname(user.getNickname());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBgImageUrl(user.getBgImageUrl());
        dto.setDesc(user.getDesc());
        dto.setGender(user.getGender());
        dto.setBirthday(user.getBirthday());
        dto.setRegion(user.getRegion());
        dto.setJob(user.getJob());
        dto.setSchool(user.getSchool());

        return Result.success(dto);
    }

    /**
     * 修改用户资料
     */
    @PutMapping("/updateInfo")
    public Result<?> updateInfo(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestBody UpdateUserInfoDTO dto
    ) {
        String token = tokenHeader.replace("Bearer ", "");
        Claims claims = JwtUtil.parseToken(token);
        Long userId = Long.valueOf(claims.getSubject());

        User user = new User();
        user.setId(userId);
        user.setNickname(dto.getNickname());
        user.setDesc(dto.getDesc());
        user.setGender(dto.getGender());
        user.setBirthday(dto.getBirthday());
        user.setRegion(dto.getRegion());
        user.setJob(dto.getJob());
        user.setSchool(dto.getSchool());

        userService.updateById(user);

        return Result.success("资料更新成功");
    }

    /**
     * 上传头像
     */
    @PostMapping("/uploadAvatar")
    public Result<?> uploadAvatar(
            @RequestHeader("Authorization") String tokenHeader,
            @RequestParam("file") MultipartFile file
    ) {
        String saveFolder = "C:\\temp\\upload\\avatar\\";
        File dir = new File(saveFolder);

        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("文件夹创建结果：" + created);

            if (!created) {
                return Result.fail(
                        "文件夹创建失败，请手动创建 "
                                + "C:\\temp\\upload\\avatar"
                );
            }
        }

        String fileName =
                UUID.randomUUID()
                        + getSuffix(file.getOriginalFilename());

        File dest = new File(dir, fileName);

        try {
            file.transferTo(dest);

            String url =
                    "http://127.0.0.1:8080/uploads/avatar/"
                            + fileName;

            String token = tokenHeader.replace("Bearer ", "");
            Claims claims = JwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());

            User user = new User();
            user.setId(userId);
            user.setAvatarUrl(url);
            userService.updateById(user);

            return Result.success(url);

        } catch (IOException e) {
            e.printStackTrace();
            return Result.fail("上传失败");
        }
    }

    private String getSuffix(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return ".jpg";
        }

        return originalName.substring(
                originalName.lastIndexOf(".")
        );
    }

    /**
     * 用户注册
     */
    @PostMapping("/userRegister")
    public Result<?> register(@RequestBody UserRegisterDTO dto) {
        User exist = userService.getByPhone(dto.getPhone());

        if (exist != null) {
            return Result.fail("该手机号已注册");
        }

        User newUser = new User();
        newUser.setPhone(dto.getPhone());
        newUser.setPassword(
                PasswordUtil.encode(dto.getPassword())
        );

        userService.addUser(newUser);

        return Result.success("注册成功");
    }

    /**
     * 账号密码登录
     */
    @PostMapping("/userLogin")
    public Result<?> loginByPwd(@RequestBody UserLoginDTO dto) {
        User user = userService.getByPhone(dto.getPhone());

        if (user == null) {
            return Result.fail("手机号不存在");
        }

        if (!PasswordUtil.match(
                dto.getPassword(),
                user.getPassword()
        )) {
            return Result.fail("密码错误");
        }

        String token = JwtUtil.generateToken(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(token);

        return Result.success(vo);
    }

    /**
     * 旧图片上传接口已停用
     */
    @PostMapping("/uploadOotd")
    public ResponseEntity<Result<?>> uploadOotd() {
        return retiredOotdEndpoint(
                "旧图片上传接口已停用，请使用 POST /api/posts/media"
        );
    }

    /**
     * 旧发布接口已停用
     */
    @PostMapping("/publishOotd")
    public ResponseEntity<Result<?>> publishOotd() {
        return retiredOotdEndpoint(
                "旧发布接口已停用，请使用 POST /api/posts"
        );
    }

    /**
     * 旧个人帖子查询接口已停用
     */
    @GetMapping("/getMyOotd")
    public ResponseEntity<Result<?>> getMyOotd() {
        return retiredOotdEndpoint(
                "旧查询接口已停用，请使用 GET /api/me/posts"
        );
    }

    /**
     * 旧删除接口已停用
     */
    @DeleteMapping("/deleteOotd/{id}")
    public ResponseEntity<Result<?>> deleteOotd(
            @PathVariable("id") Long id
    ) {
        return retiredOotdEndpoint(
                "旧删除接口已停用，请使用 DELETE /api/posts/{postId}"
        );
    }

    private ResponseEntity<Result<?>> retiredOotdEndpoint(
            String message
    ) {
        Result<Object> result = Result.fail(message);
        result.setCode(410);

        return ResponseEntity.status(410).body(result);
    }
}