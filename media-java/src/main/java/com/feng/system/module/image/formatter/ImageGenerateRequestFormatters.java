package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.List;
import java.util.Map;

public class ImageGenerateRequestFormatters {
    private final List<ImageGenerateRequestFormatter> formatters = List.of(
            new GptImage2AspectRatioRequestFormatter(),
            new GptImage2SizedRequestFormatter(),
            new GenericImageRequestFormatter()
    );

    public Map<String, Object> format(String modelKey, ImageGenerateRequest request) {
        return formatters.stream()
                .filter(formatter -> formatter.supports(modelKey))
                .findFirst()
                .orElseThrow()
                .format(request);
    }
}
