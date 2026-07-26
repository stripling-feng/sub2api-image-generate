package com.feng.system.module.image.dto;

/**
 * 生成请求受理成功的响应。
 *
 * @param requestId 本次生成请求的唯一 ID,用于后续查询结果
 * @param count     本次请求受理的任务(图片)数量
 */
public record GenerationAcceptedResponse(String requestId, int count) {
}
