package com.feng.system.module.image.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ImageGenerateRequest {
    private String model;
    private String prompt;
    private String negativePrompt;
    private Integer count;
    private String size;
    private String aspectRatio;
    private String customAspectRatio;
    private String quality;
    private String style;
    private String responseFormat;
    private String outputFormat;
    private Map<String, Object> parameters;
    private Map<String, Object> extraParams;
    private List<Object> referenceImages;
    private List<String> referenceImageUrls;
    private Object mask;
    private String maskUrl;

    public Map<String, Object> clientPayload() {
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "model", model);
        put(value, "prompt", prompt);
        put(value, "negativePrompt", negativePrompt);
        put(value, "count", count);
        put(value, "size", size);
        put(value, "aspectRatio", aspectRatio);
        put(value, "customAspectRatio", customAspectRatio);
        put(value, "quality", quality);
        put(value, "style", style);
        put(value, "responseFormat", responseFormat);
        put(value, "outputFormat", outputFormat);
        put(value, "parameters", parameters);
        put(value, "extraParams", extraParams);
        put(value, "referenceImages", referenceImages);
        put(value, "referenceImageUrls", referenceImageUrls);
        put(value, "mask", mask);
        put(value, "maskUrl", maskUrl);
        return value;
    }

    public Map<String, Object> suppliedParameters() {
        Map<String, Object> value = new LinkedHashMap<>();
        if (parameters != null) value.putAll(parameters);
        if (extraParams != null) value.putAll(extraParams);
        return value;
    }

    public boolean hasJsonImageInputs() {
        return referenceImages != null && !referenceImages.isEmpty() || mask != null;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
