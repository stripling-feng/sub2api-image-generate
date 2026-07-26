package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.Map;

/**
 * 图片生成请求格式化器接口。
 * 按模型差异把统一的生成请求转换为上游 API 所需的参数 Map。
 */
public interface ImageGenerateRequestFormatter {
    /** 判断当前格式化器是否支持指定模型 */
    boolean supports(String modelKey);
    /** 将生成请求转换为发送给上游的参数 Map */
    Map<String, Object> format(ImageGenerateRequest request);
}
