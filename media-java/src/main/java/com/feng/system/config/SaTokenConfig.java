package com.feng.system.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            StpUtil.checkLogin();
            StpUtil.renewTimeout(3600);
        }))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/system/business-dicts/site-config",
                        "/api/docs/**",
                        "/api/images/**",
                        "/api/videos/**",
                        "/api/video-jobs/**",
                        "/api/jobs/**",
                        "/img/**",
                        "/uploads/**"
                );
    }
}
