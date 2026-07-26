package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型参数规则工具类：依据模型的参数 schema 校验用户传入的参数（选项合法性、尺寸/比例限制），
 * 并按「默认参数 &lt; 用户参数 &lt; schema 默认值兜底」的顺序合并出最终请求参数。
 */
public final class ImageModelRules {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageModelRules() {}

    /**
     * 校验并合并模型参数。
     *
     * @param schemaJson   模型参数 schema（JSON 数组，每项含 key/type/options 等定义）
     * @param defaultsJson 模型默认参数（JSON 对象），作为合并基底
     * @param supplied     用户传入的参数，仅接受 schema 中声明过的字段
     * @return 合并后的最终请求参数
     */
    public static Map<String, Object> merge(String schemaJson, String defaultsJson, Map<String, Object> supplied) {
        try {
            List<Map<String, Object>> schema = JSON.readValue(schemaJson, new TypeReference<>() {});
            Map<String, Object> result = JSON.readValue(defaultsJson, new TypeReference<>() {});
            Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
            for (Map<String, Object> field : schema) fields.put(String.valueOf(field.get("key")), field);
            // 只接受 schema 中声明的字段，空值/空串忽略；校验通过后覆盖默认参数
            for (Map.Entry<String, Object> entry : supplied.entrySet()) {
                Map<String, Object> field = fields.get(entry.getKey());
                if (field == null || entry.getValue() == null) continue;
                if (entry.getValue() instanceof String text && text.isBlank()) continue;
                validateOption(field, entry.getValue());
                validateSize(field, entry.getValue());
                result.put(entry.getKey(), entry.getValue());
            }
            // 仍缺失的字段用 schema 中的 default 值兜底
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

    // select 类型字段：值必须在 options 白名单内（支持 {value,label} 对象或纯值两种写法）
    private static void validateOption(Map<String, Object> field, Object value) {
        if (!"select".equals(field.get("type")) || !(field.get("options") instanceof List<?> options)) return;
        boolean allowed = options.stream().anyMatch(option -> option instanceof Map<?, ?> map
                ? String.valueOf(map.get("value")).equals(String.valueOf(value))
                : String.valueOf(option).equals(String.valueOf(value)));
        if (!allowed) throw new ImageApiException(422, "Invalid model parameter: " + field.get("key"));
    }

    // size 类型字段：支持 "宽:高" 比例或 "宽x高" 像素两种格式，分别校验比例范围与像素约束
    private static void validateSize(Map<String, Object> field, Object value) {
        if (!"size".equals(field.get("type"))) return;
        String text = String.valueOf(value).trim().toLowerCase();
        // 比例格式（如 16:9）：只校验宽高比在 [1/maxRatio, maxRatio] 之内
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
        // 像素格式：宽高须为 multiple 的倍数，且满足最长边、总像素与宽高比限制
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
