package com.jnclub.music.common.ratelimit;

import com.jnclub.common.cache.CacheKey;
import com.jnclub.common.cache.RateLimiter;
import com.jnclub.music.common.enums.ErrorCode;
import com.jnclub.music.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 匿名音乐接口限流拦截器。
 *
 * <p>音乐模块按 {@code X-Device-Id} 匿名隔离，且接口完全放行、无任何频控；
 * 蓝奏云直链获取是重操作，最易被刷。本拦截器按设备维度做固定窗口限流，
 * 超限抛 {@link BusinessException}({@link ErrorCode#RATE_LIMITED})，由
 * {@code MusicGlobalExceptionHandler} 统一返回 429。</p>
 *
 * <p>路径：匹配重写后的 {@code /api/v1/**}（音乐专属前缀，不影响 JNClub 主业务）。</p>
 */
@Component
public class MusicRateLimitInterceptor implements HandlerInterceptor {

    /** 默认限流：单设备每分钟 120 次（宽松，观察后收紧） */
    static final int RATE_LIMIT = 120;
    static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;

    public MusicRateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String deviceId = request.getHeader("X-Device-Id");
        if (!StringUtils.hasText(deviceId)) {
            deviceId = "anonymous";
        }
        String key = CacheKey.rate("music", deviceId.trim());
        if (!rateLimiter.tryAcquire(key, RATE_LIMIT, RATE_WINDOW)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
        return true;
    }
}
