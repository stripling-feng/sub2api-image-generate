package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.Map;

public class GenericImageRequestFormatter implements ImageGenerateRequestFormatter {
    @Override
    public boolean supports(String modelKey) {
        return false;
    }

    @Override
    public Map<String, Object> format(ImageGenerateRequest request) {
        return Map.of();
    }
}
