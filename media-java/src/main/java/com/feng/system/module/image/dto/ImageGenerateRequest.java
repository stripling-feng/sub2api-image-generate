package com.feng.system.module.image.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ImageGenerateRequest {
    private String model;
    private String prompt;
    private Boolean async;
    private Integer count;
    private String size;
    @JsonProperty("aspect_ratio")
    private String aspectRatio;
    private String quality;
    private List<String> images;

    public Map<String, Object> clientPayload() {
        Map<String, Object> value = new LinkedHashMap<>();
        put(value, "model", model);
        put(value, "prompt", prompt);
        put(value, "async", async);
        put(value, "count", count);
        put(value, "size", size);
        put(value, "aspect_ratio", aspectRatio);
        put(value, "quality", quality);
        put(value, "images", images);
        return value;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) map.put(key, value);
    }
}
