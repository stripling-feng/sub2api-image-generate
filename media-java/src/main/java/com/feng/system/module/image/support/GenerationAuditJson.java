package com.feng.system.module.image.support;

import com.feng.system.module.image.exception.ImageApiException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 生成任务审计 JSON 工具类:负责将请求/响应序列化为审计日志,
 * 序列化时自动脱敏密钥类字段并把 Base64 大块数据替换为占位符;
 * 同时提供拒绝 Base64 输入的校验能力。
 */
public final class GenerationAuditJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/\\r\\n]+={0,2}$");
    private static final Set<String> SECRET_KEYS = Set.of(
            "authorization", "cookie", "set-cookie", "api_key", "apikey", "token", "access_token", "password");

    private GenerationAuditJson() {}

    /**
     * 校验入参中不得包含 Base64 图片数据(本系统只支持 URL 输入输出),发现则抛出 422。
     */
    public static void rejectBase64(Object value) {
        if (containsBase64(value, null)) throw new ImageApiException(422, "Base64 inputs and outputs are not supported.");
    }

    /**
     * 生成审计请求 JSON:记录脱敏后的客户端请求,upstream 字段先置空待后续补充。
     */
    public static String request(Object client) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("client", sanitize(client, null)); value.put("upstream", null);
        return write(value, "Unable to serialize generation audit request.");
    }

    /**
     * 在已有审计请求 JSON 中补充脱敏后的上游请求内容。
     *
     * @param request 之前 {@link #request} 生成的 JSON 字符串
     */
    public static String withUpstream(String request, Object upstream) {
        try {
            Map<String, Object> value = JSON.readValue(request, new TypeReference<>() {});
            value.put("upstream", sanitize(upstream, null));
            return JSON.writeValueAsString(value);
        } catch (Exception e) { throw new IllegalArgumentException("Unable to serialize generation upstream request.", e); }
    }

    /**
     * 将任意对象脱敏后序列化为 JSON 字符串。
     */
    public static String stringify(Object value) {
        return write(sanitize(value, null), "Unable to serialize generation audit data.");
    }

    private static String write(Object value, String message) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException(message, e); }
    }

    /**
     * 向审计响应 JSON 数组追加一条记录(包含阶段名、时间戳与脱敏后的载荷)。
     *
     * @param existing 已有的 JSON 数组字符串,为空时新建数组
     * @param phase    记录所属阶段标识(如轮询、回调等)
     */
    public static String append(String existing, String phase, Object payload) {
        try {
            List<Object> entries = existing == null || existing.isBlank()
                    ? new ArrayList<>() : JSON.readValue(existing, new TypeReference<>() {});
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("phase", phase); entry.put("at", LocalDateTime.now().toString());
            entry.put("payload", sanitize(payload, null)); entries.add(entry);
            return JSON.writeValueAsString(entries);
        } catch (Exception e) { throw new IllegalArgumentException("Unable to serialize generation audit response.", e); }
    }

    // 递归脱敏:密钥字段替换为 "redacted",Base64/数据 URI 内容替换为 "base64",容器类型逐项处理
    private static Object sanitize(Object value, String key) {
        if (key != null && SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT))) return "redacted";
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof byte[]) return "base64";
        if (value instanceof CharSequence chars) {
            String text = chars.toString();
            if (text.regionMatches(true, 0, "data:", 0, 5) && text.toLowerCase(Locale.ROOT).contains(";base64,")) return "base64";
            if (key != null && (key.toLowerCase(Locale.ROOT).contains("base64") || key.toLowerCase(Locale.ROOT).contains("b64"))) return "base64";
            // 长度 >=128 且符合 Base64 特征的长字符串按编码内容处理,避免审计日志膨胀
            return text.length() >= 128 && text.length() % 4 == 0 && BASE64.matcher(text).matches() ? "base64" : text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((itemKey, item) -> {
                String name = String.valueOf(itemKey);
                result.put(name, sanitize(item, name));
            });
            return result;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> result = new ArrayList<>();
            for (Object item : values) result.add(sanitize(item, key));
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) result.add(sanitize(Array.get(value, i), key));
            return result;
        }
        return String.valueOf(value);
    }

    // 递归检测是否包含 Base64 内容:检查字段名特征(b64/base64/response_format)、
    // data URI 前缀,以及媒体类字段或超长字符串的 Base64 编码特征
    private static boolean containsBase64(Object value, String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if ("responseformat".equals(normalized) && "b64_json".equalsIgnoreCase(String.valueOf(value))) return true;
        if ((normalized.contains("base64") || normalized.contains("b64")) && value != null) return true;
        if (value instanceof CharSequence chars) {
            String text = chars.toString();
            boolean mediaField = normalized.contains("image") || normalized.contains("mask") || "data".equals(normalized);
            boolean encoded = text.length() >= 8 && text.length() % 4 == 0 && BASE64.matcher(text).matches();
            return text.regionMatches(true, 0, "data:", 0, 5) && text.toLowerCase(Locale.ROOT).contains(";base64,")
                    || encoded && (mediaField || text.length() >= 128);
        }
        if (value instanceof Map<?, ?> map)
            return map.entrySet().stream().anyMatch(entry -> containsBase64(entry.getValue(), String.valueOf(entry.getKey())));
        if (value instanceof Iterable<?> values) {
            for (Object item : values) if (containsBase64(item, key)) return true;
        }
        if (value != null && value.getClass().isArray())
            for (int i = 0; i < Array.getLength(value); i++) if (containsBase64(Array.get(value, i), key)) return true;
        return false;
    }
}
