package com.jnclub.music.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.jnclub.music.common.logging.RequestLoggingInterceptor;

// Bean 名显式指定，避免与 com.jnclub.common.config.WebMvcConfig 重名冲突
@Configuration("musicWebMvcConfig")
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name())
                .allowCredentials(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径直接访问音乐页面（原来需要 /admin/ 后缀）
        registry.addViewController("/").setViewName("forward:/index.html");
        // 核心：无尾斜杠入口统一重定向，避免相对静态资源被浏览器解析到 /music/assets。
        registry.addViewController("/admin").setViewName("redirect:/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/{path:[^\\.]*}").setViewName("forward:/admin/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 并入 JNClub 单体后仅拦截音乐路径，避免为 JNClub 其它接口打请求日志
        registry.addInterceptor(new RequestLoggingInterceptor()).addPathPatterns("/music/**");
    }

    @Bean
    public FilterRegistrationBean<TraceIdConfig> traceIdFilter() {
        FilterRegistrationBean<TraceIdConfig> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdConfig());
        registration.addUrlPatterns("/music/*");
        return registration;
    }
}
