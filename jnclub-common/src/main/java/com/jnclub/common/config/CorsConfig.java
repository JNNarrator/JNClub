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

    public CorsConfig(
            @Value("${jnclub.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String allowedOrigins,
            @Value("${jnclub.frontend-url:}") String frontendUrl) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll);
        // 自动将前端部署域名加入白名单（用 URI 解析出 scheme://host[:port]，去掉任何路径）
        if (!frontendUrl.isBlank()) {
            String origin = toOrigin(frontendUrl);
            if (origin != null && !origins.contains(origin)) {
                origins.add(origin);
            }
        }
        this.allowedOrigins = List.copyOf(origins);
    }

    /**
     * 从任意 URL 提取 CORS origin（scheme://host[:port]）。
     * 例如 {@code https://jiangnan.88933.vip/jnclub} → {@code https://jiangnan.88933.vip}。
     * 解析失败返回 null（不阻断启动）。
     */
    private static String toOrigin(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder()
                    .append(uri.getScheme().toLowerCase())
                    .append("://")
                    .append(uri.getHost());
            int port = uri.getPort();
            boolean defaultPort = (port == -1)
                    || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
            if (!defaultPort) {
                sb.append(':').append(port);
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
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
