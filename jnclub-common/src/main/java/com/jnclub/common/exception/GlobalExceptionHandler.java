package com.jnclub.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.jnclub.common.model.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleNotLoginException(NotLoginException e) {
        log.debug("未登录访问拦截: {}", e.getMessage());
        return R.fail(401, "未登录或会话已过期");
    }

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统内部错误");
    }
}
