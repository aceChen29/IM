package com.ace.im.user.config;

import java.util.ArrayList;
import java.util.Arrays;


import com.ace.im.user.common.interceptors.JwtHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private JwtHandler jwtHandler;

    private final ArrayList<String> excludePath = new ArrayList<>(
            Arrays.asList("/api/user/login/code",
                         "/api/user/register",
                         "/api/user/sendCaptcha",
                         "/api/user/login/password",
                         "/api/user/refresh"
                         ));

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtHandler).addPathPatterns("/**").excludePathPatterns(excludePath);
    }


    /**
     * 配置全局跨域规则，允许前端从不同源访问后端接口。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 对所有接口路径生效
                .allowedOriginPatterns("*") // 允许任意来源；生产环境建议改为指定的前端域名
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的请求方法
                .allowedHeaders("*") // 允许请求携带任意请求头
                .allowCredentials(true) // 允许跨域请求携带 Cookie 等凭据
                .maxAge(3600); // 浏览器将预检请求结果缓存 3600 秒
    }
}