package com.jnclub.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 配置 —— 显式白名单模式。
 *
 * <p><b>安全约束</b>：禁止出现「通配来源 + {@code allowCredentials(true)}」组合。
 * Spring 在 {@code addAllowedOriginPattern("*")} 与凭据同时开启时会回显请求方 Origin，
 * 等价于允许任意站点带凭据跨域调用后端全部接口。</p>
 *
 * <p>白名单来源由 {@code jnclub.cors.allowed-origins}（逗号分隔）配置，默认仅本地 Vite dev。
 * 三种调用方都无需额外来源：同源部署（nginx 托管前端 + 反代 {@code /api}）不触发 CORS、
 * 本地 dev 走 Vite 代理属同源、浏览器扩展由 manifest {@code host_permissions} 覆盖。</p>
 *
 * <p>凭据：前端以 {@code jn-token} 请求头携带身份（非 Cookie），因此
 * {@code allowCredentials(false)} 足够，同时杜绝凭据型跨站读取。</p>
 */
@Configuration
public class CorsConfig {

    /** 默认白名单：仅本地 Vite dev。线上同源部署无需任何跨域来源。 */
    static final String DEFAULT_ALLOWED_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173";

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${jnclub.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 来源必须显式列举；setAllowedOrigins 不接受 "*" 与 allowCredentials(true) 共存
        config.setAllowedOrigins(allowedOrigins);
        // 请求头放开（白名单来源已限定），避免因漏列自定义头导致预检失败
        config.addAllowedHeader("*");
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
