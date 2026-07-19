package com.feng.system.module.image;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ImageModelRules {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageModelRules() {}

    public static Map<String, Object> merge(String schemaJson, String defaultsJson, Map<String, Object> supplied) {
        try {
            List<Map<String, Object>> schema = JSON.readValue(schemaJson, new TypeReference<>() {});
            Map<String, Object> result = JSON.readValue(defaultsJson, new TypeReference<>() {});
            Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
            for (Map<String, Object> field : schema) fields.put(String.valueOf(field.get("key")), field);
            for (Map.Entry<String, Object> entry : supplied.entrySet()) {
                Map<String, Object> field = fields.get(entry.getKey());
                if (field == null || entry.getValue() == null) continue;
                if (entry.getValue() instanceof String text && text.isBlank()) continue;
                validateOption(field, entry.getValue());
                validateSize(field, entry.getValue());
                result.put(entry.getKey(), entry.getValue());
            }
            for (Map<String, Object> field : schema) {
                String key = String.valueOf(field.get("key"));
                if (!result.containsKey(key) && field.get("default") != null) result.put(key, field.get("default"));
            }
            return result;
        } catch (ImageApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageApiException(500, "Invalid image model configuration.");
        }
    }

    private static void validateOption(Map<String, Object> field, Object value) {
        if (!"select".equals(field.get("type")) || !(field.get("options") instanceof List<?> options)) return;
        boolean allowed = options.stream().anyMatch(option -> option instanceof Map<?, ?> map
                ? String.valueOf(map.get("value")).equals(String.valueOf(value))
                : String.valueOf(option).equals(String.valueOf(value)));
        if (!allowed) throw new ImageApiException(422, "Invalid model parameter: " + field.get("key"));
    }

    private static void validateSize(Map<String, Object> field, Object value) {
        if (!"size".equals(field.get("type"))) return;
        String text = String.valueOf(value).trim().toLowerCase();
        if (text.matches("\\d+:\\d+")) {
            String[] parts = text.split(":");
            double ratio = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            double maxRatio = number(field.get("maxRatio"), 3);
            if (ratio < 1 / maxRatio || ratio > maxRatio) throw invalid(field);
            return;
        }
        if (!text.matches("\\d+x\\d+")) throw invalid(field);
        String[] parts = text.split("x");
        long width = Long.parseLong(parts[0]), height = Long.parseLong(parts[1]);
        long pixels = width * height, multiple = (long) number(field.get("multiple"), 16);
        double ratio = (double) width / height, maxRatio = number(field.get("maxRatio"), 3);
        if (width <= 0 || height <= 0 || width % multiple != 0 || height % multiple != 0
                || Math.max(width, height) > number(field.get("maxSide"), 3840)
                || pixels < number(field.get("minPixels"), 0) || pixels > number(field.get("maxPixels"), Long.MAX_VALUE)
                || ratio < 1 / maxRatio || ratio > maxRatio) throw invalid(field);
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static ImageApiException invalid(Map<String, Object> field) {
        return new ImageApiException(422, "Invalid model parameter: " + field.get("key"));
    }
}
