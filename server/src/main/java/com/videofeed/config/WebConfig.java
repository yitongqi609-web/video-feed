package com.videofeed.config;

import com.videofeed.auth.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                // 注册/登录/刷新是匿名接口；其余全部需要 Access Token
                .excludePathPatterns("/api/auth/register", "/api/auth/login", "/api/auth/refresh");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地样例视频（server/media/），演示环境不依赖任何外部视频站
        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:media/")
                .setCachePeriod(3600);
    }
}
