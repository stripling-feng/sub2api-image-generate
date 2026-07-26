package com.feng.system.module.image.exception;

import com.feng.system.common.api.ApiResponse;
import com.feng.system.module.image.dto.ValidationErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 图片/视频模块的全局异常处理器。
 * 拦截 image、video 两个模块的异常,统一转换为 ApiResponse 格式的响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"com.feng.system.module.image", "com.feng.system.module.video"})
public class ImageApiExceptionHandler {

    /**
     * 处理业务异常,按异常自带的 HTTP 状态码返回。
     */
    @ExceptionHandler(ImageApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleImageApi(ImageApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
                new ApiResponse<>(exception.getStatus(), exception.getMessage(), exception.getPayload()));
    }

    /**
     * 处理参数校验失败,返回 422 及逐字段的校验错误列表。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ValidationErrorResponse>>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.unprocessableEntity().body(new ApiResponse<>(422, "Invalid request.",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ValidationErrorResponse(new String[]{error.getField()}, error.getDefaultMessage()))
                        .toList()));
    }

    /**
     * 兜底处理未预期的异常,统一返回 500。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(new ApiResponse<>(500,
                exception.getMessage() == null ? "Internal server error." : exception.getMessage(), null));
    }
}
