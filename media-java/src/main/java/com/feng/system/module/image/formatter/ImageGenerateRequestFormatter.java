package com.feng.system.module.image.formatter;

import com.feng.system.module.image.dto.ImageGenerateRequest;

import java.util.Map;

public interface ImageGenerateRequestFormatter {
    boolean supports(String modelKey);
    Map<String, Object> format(ImageGenerateRequest request);
}
