package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class GptImage2SizedRequestFormatter implements ImageGenerateRequestFormatter {
    private static final Set<String> MODELS = Set.of("gpt-image-2-1k", "gpt-image-2-2k", "gpt-image-2-4k");

    @Override
    public boolean supports(String modelKey) {
        return MODELS.contains(modelKey);
    }

    @Override
    public Map<String, Object> format(ImageGenerateRequest request) {
        Map<String, Object> value = new LinkedHashMap<>(request.suppliedParameters());
        put(value, "aspect_ratio", request.getAspectRatio());
        put(value, "size", request.getSize());
        put(value, "quality", request.getQuality());
        return value;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
