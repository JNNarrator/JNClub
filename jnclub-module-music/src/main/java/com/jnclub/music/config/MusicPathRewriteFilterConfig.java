package com.jnclub.music.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * /music 路径重写过滤器
 *
 * <p>JNMusic 并入 JNClub 单体后，对外 URL 保持 <code>/music/api/v1/...</code> 不变，
 * 而音乐模块的 Controller 统一映射在 <code>/api/v1/...</code>（未带 /music 前缀）。
 * 本过滤器将 <code>/music/api/**</code> 请求重写为 <code>/api/**</code>，使其命中音乐 Controller。
 *
 * <p>需注册为最高优先级 Filter（在 DispatcherServlet 之前执行，早于 Spring Security / CORS Filter），
 * 这样 Servlet 容器按重写后的 URI 匹配 <code>@RequestMapping</code>。
 */
@Configuration
public class MusicPathRewriteFilterConfig {

    private static final String MUSIC_API_PREFIX = "/music/api/";

    @Bean
    public FilterRegistrationBean<Filter> musicPathRewriteFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((ServletRequest request, ServletResponse response, FilterChain chain) -> {
            if (request instanceof HttpServletRequest httpRequest) {
                String uri = httpRequest.getRequestURI();
                if (uri.startsWith(MUSIC_API_PREFIX)) {
                    chain.doFilter(new RewrittenRequestWrapper(httpRequest, uri.substring("/music".length())), response);
                    return;
                }
            }
            chain.doFilter(request, response);
        });
        // order = 最高优先级：在 OrderedFilter.HIGHEST_PRECEDENCE 前执行，先于 CORS/鉴权过滤器
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/music/api/*");
        return registration;
    }

    /**
     * 请求包装器：getRequestURI 返回去除 /music 前缀后的 URI。
     */
    private static class RewrittenRequestWrapper extends HttpServletRequestWrapper {

        private final String rewrittenUri;

        RewrittenRequestWrapper(HttpServletRequest request, String rewrittenUri) {
            super(request);
            this.rewrittenUri = rewrittenUri;
        }

        @Override
        public String getRequestURI() {
            return rewrittenUri;
        }
    }
}
