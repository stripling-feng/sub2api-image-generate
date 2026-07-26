package com.feng.system.module.image.exception;

import lombok.Getter;

/**
 * 图片/视频 API 业务异常。
 * 携带 HTTP 状态码、业务错误码与附加数据,由全局异常处理器转换为统一响应。
 */
@Getter
public class ImageApiException extends RuntimeException {
    /** HTTP 状态码 */
    private final int status;
    /** 业务错误码,可为空 */
    private final String code;
    /** 附加的错误数据,随响应体返回 */
    private final Object payload;

    /**
     * 构造仅含状态码与错误信息的异常。
     */
    public ImageApiException(int status, String message) {
        this(status, message, null, null);
    }

    /**
     * 构造完整异常。
     *
     * @param status  HTTP 状态码
     * @param message 错误信息
     * @param code    业务错误码
     * @param payload 附加错误数据
     */
    public ImageApiException(int status, String message, String code, Object payload) {
        super(message);
        this.status = status;
        this.code = code;
        this.payload = payload;
    }
}
