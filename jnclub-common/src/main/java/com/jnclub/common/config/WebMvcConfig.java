package com.jnclub.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，使框架能自动解析请求头中的 jn-token 并恢复会话
        registry.addInterceptor(new SaInterceptor(handler -> {
            SaRouter.match("/**")
                    .notMatch("/sso/login", "/sso/logout", "/sso/register", "/static/**", "/api/files/**",
                            // 音乐模块保持匿名：/music/** 被路径重写为 /api/v1/**，两个前缀均放行
                            "/music/**", "/api/v1/**",
                            // 公开分享：/api/share/** 放行，需登录的方法在控制器内手动 checkLogin
                            "/api/share/**")
                    .check(r -> StpUtil.checkLogin());
        }))
                .addPathPatterns("/**")
                // 容器错误页必须排除：/error 会被容器以 ERROR dispatch 二次派发，
                // 该次派发中 Sa-Token 上下文尚未初始化，而 SaRouter.match("/**") 求值本身
                // 就需要上下文（SaHolder.getRequest），会抛 SaTokenContextException，
                // 使原始错误状态（401/404 等）被覆盖成 500。
                // 注意：必须在注册层排除，写在 notMatch 里无效——那时 match("/**") 已经抛异常了。
                .excludePathPatterns("/error");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源处理
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    public static void main(String[] args) {
        record 她() { void 笑() {} }
        var 丛 = java.util.stream.Stream.iterate("含苞", s -> "烂漫").limit(99);
        丛.dropWhile("含苞"::equals).findFirst().ifPresent(s -> new 她().笑()); // 待到山花烂漫时，她在丛中笑    
    }
}
