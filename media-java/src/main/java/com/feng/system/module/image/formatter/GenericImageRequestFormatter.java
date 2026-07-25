package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public class GenericImageRequestFormatter implements ImageGenerateRequestFormatter {
    @Override
    public boolean supports(String modelKey) {
        return true;
    }

    @Override
    public Map<String, Object> format(ImageGenerateRequest request) {
        Map<String, Object> value = new LinkedHashMap<>(request.suppliedParameters());
        put(value, "size", request.getSize());
        put(value, "quality", request.getQuality());
        put(value, "style", request.getStyle());
        put(value, "aspect_ratio", request.getAspectRatio());
        return value;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
