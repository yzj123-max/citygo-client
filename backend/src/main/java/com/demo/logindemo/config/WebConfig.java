package com.demo.logindemo.config;

import com.demo.logindemo.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addResourceHandlers(
            ResourceHandlerRegistry registry
    ) {
        // 保留头像静态资源访问
        registry.addResourceHandler("/uploads/avatar/**")
                .addResourceLocations(
                        "file:C:/temp/upload/avatar/"
                );

        // 旧的 /uploads/ootd/** 本地图片映射已移除。
        // 新帖子图片由权限校验后的接口生成TOS临时签名地址。
    }

    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/userRegister",
                        "/userLogin",
                        "/sendCode",
                        "/userLogin/code",
                        "/uploads/avatar/**",
                        "/api/ai/**"
                );
    }
}