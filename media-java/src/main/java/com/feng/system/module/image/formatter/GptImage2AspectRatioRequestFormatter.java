package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public class GptImage2AspectRatioRequestFormatter implements ImageGenerateRequestFormatter {
    @Override
    public boolean supports(String modelKey) {
        return "gpt-image-2".equals(modelKey);
    }

    @Override
    public Map<String, Object> format(ImageGenerateRequest request) {
        Map<String, Object> value = new LinkedHashMap<>(request.suppliedParameters());
        put(value, "size", firstText(request.getAspectRatio(), request.getSize()));
        return value;
    }

    private static String firstText(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
