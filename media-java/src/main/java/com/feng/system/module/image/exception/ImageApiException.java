package com.feng.system.module.image.exception;

import lombok.Getter;

@Getter
public class ImageApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Object payload;

    public ImageApiException(int status, String message) {
        this(status, message, null, null);
    }

    public ImageApiException(int status, String message, String code, Object payload) {
        super(message);
        this.status = status;
        this.code = code;
        this.payload = payload;
    }
}
