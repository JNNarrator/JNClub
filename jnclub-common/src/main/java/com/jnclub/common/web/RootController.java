package com.jnclub.common.web;

import com.jnclub.common.model.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根路径健康检查。
 *
 * <p>存在的必要性：若不给 "/" 注册显式处理器，Spring Boot 会由 welcome-page 映射
 * （WelcomePageHandlerMapping）接管该路径，其 handler 不是 HandlerMethod，
 * 导致 Sa-Token 拦截器抛出的 NotLoginException 无法被 GlobalExceptionHandler 处理，
 * 异常直接逃逸到容器，使未登录访问 "/" 返回 500（而非预期的 401）。
 *
 * <p>这里显式映射后，"/" 变成常规 HandlerMethod，未登录返回 401、已登录返回服务状态。
 */
@RestController
public class RootController {

    /** 后端根路径：未登录 401；已登录返回服务存活信息 */
    @GetMapping("/")
    public R<Map<String, Object>> root() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "jnclub-gateway");
        info.put("status", "ok");
        return R.ok(info);
    }
}
