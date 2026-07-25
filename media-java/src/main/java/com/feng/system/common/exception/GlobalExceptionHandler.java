package com.feng.system.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.feng.system.common.api.ApiResponse;
import com.feng.system.common.submit.DuplicateSubmitException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException ex) {
        log.warn("参数校验异常: {}", ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        String message = ex.getBindingResult().getFieldError() != null
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException ex) {
        log.warn("约束校验异常: {}", ex.getMessage());
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(DuplicateSubmitException.class)
    public ApiResponse<Void> handleDuplicateSubmit(DuplicateSubmitException ex) {
        log.warn("重复提交: {}", ex.getMessage());
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException ex) {
        String message;
        int code;
        if (NotLoginException.BE_REPLACED.equals(ex.getType())) {
            message = "您的账号已在其他设备登录";
            code = 401001;
        } else if (NotLoginException.KICK_OUT.equals(ex.getType())) {
            message = "您已被踢下线";
            code = 401002;
        } else {
            message = "未登录或登录已过期";
            code = 401;
        }
        log.warn("未登录: type={}, message={}", ex.getType(), message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse<>(code, message, null));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotPermission(NotPermissionException ex) {
        log.warn("无访问权限: permission={}", ex.getPermission());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiResponse<>(403, "无访问权限", null));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("系统异常", ex);
        return ApiResponse.fail("系统繁忙，请稍后再试");
    }
}
