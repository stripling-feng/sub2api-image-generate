package com.feng.system.module.image.dto;

/**
 * 参数校验错误的响应。
 *
 * @param path    出错字段的路径(按层级拆分的字段名数组)
 * @param message 校验失败的错误描述
 */
public record ValidationErrorResponse(String[] path, String message) {
}
