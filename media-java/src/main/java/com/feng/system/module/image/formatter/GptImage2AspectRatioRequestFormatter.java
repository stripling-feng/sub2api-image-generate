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
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "model", request.getModel());
        put(value, "prompt", request.getPrompt());
        put(value, "async", request.getAsync());
        put(value, "size", request.getSize());
        put(value, "images", request.getImages());
        return value;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
