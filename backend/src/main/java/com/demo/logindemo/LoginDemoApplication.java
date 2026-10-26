package com.demo.logindemo;

import com.demo.logindemo.config.AgentConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import java.util.Locale;

@SpringBootApplication
@MapperScan("com.demo.logindemo.mapper")
@EnableConfigurationProperties(AgentConfig.class)
@EnableAsync // 新增这一行！开启异步
public class LoginDemoApplication {
    public static void main(String[] args) {
        // 下面两行是新增，解决TOS中文日期报错
        Locale.setDefault(Locale.ENGLISH);
        System.setProperty("user.language", "en");

        SpringApplication.run(LoginDemoApplication.class, args);
    }
}