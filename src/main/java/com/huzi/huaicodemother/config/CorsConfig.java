package com.huzi.huaicodemother.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 全局显式 CORS 白名单及个人中心写接口来源检查。 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final AllowedOrigins allowedOrigins;
    private final PersonalCenterOriginInterceptor originInterceptor;

    public CorsConfig(AllowedOrigins allowedOrigins, PersonalCenterOriginInterceptor originInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.originInterceptor = originInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 同源请求由 Spring MVC 自动识别，无需将部署主机加入跨域白名单。
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOrigins(allowedOrigins.getOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 在拦截器内按去除 context-path 的路径判断，也适用于 /api 部署。
        registry.addInterceptor(originInterceptor);
    }
}
