package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;
import com.feng.system.module.image.exception.ImageApiException;

import java.util.List;
import java.util.Map;

/**
 * 图片生成请求格式化器注册表。
 * 按注册顺序选择第一个支持该模型的格式化器,通用格式化器兜底。
 */
public class ImageGenerateRequestFormatters {
    // 注意顺序:专用格式化器在前,通用兜底格式化器必须放最后
    private final List<ImageGenerateRequestFormatter> formatters = List.of(
            new GptImage2AspectRatioRequestFormatter(),
            new GptImage2SizedRequestFormatter()
    );

    /**
     * 根据模型标识选择匹配的格式化器并转换请求参数。
     *
     * @param modelKey 模型标识
     * @param request  统一的图片生成请求
     */
    public Map<String, Object> format(String modelKey, ImageGenerateRequest request) {
        return formatters.stream()
                .filter(formatter -> formatter.supports(modelKey))
                .findFirst()
                .orElseThrow(() -> new ImageApiException(422, "Unsupported image model."))
                .format(request);
    }
}
