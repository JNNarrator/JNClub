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
import com.jnclub.music.common.ratelimit.MusicRateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;

// Bean 名显式指定，避免与 com.jnclub.common.config.WebMvcConfig 重名冲突
@Configuration("musicWebMvcConfig")
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private MusicRateLimitInterceptor musicRateLimitInterceptor;

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
        // 匿名音乐接口限流：按 X-Device-Id 维度。
        // 路径重写过滤器仅重写 getRequestURI，拦截器路径解析在不同版本下可能用原始 servletPath(/music/api/**)
        // 或重写后 URI(/api/v1/**)，故两个模式都注册，确保限流生效（二者均属音乐模块专属前缀）
        registry.addInterceptor(musicRateLimitInterceptor)
                .addPathPatterns("/music/api/**", "/api/v1/**");
    }

    @Bean
    public FilterRegistrationBean<TraceIdConfig> traceIdFilter() {
        FilterRegistrationBean<TraceIdConfig> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdConfig());
        registration.addUrlPatterns("/music/*");
        return registration;
    }
}
