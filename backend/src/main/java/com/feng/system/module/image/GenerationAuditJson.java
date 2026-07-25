package com.feng.system.module.image;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

public final class GenerationAuditJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/\\r\\n]+={0,2}$");
    private static final Set<String> SECRET_KEYS = Set.of(
            "authorization", "cookie", "set-cookie", "api_key", "apikey", "token", "access_token", "password");

    private GenerationAuditJson() {}

    public static void rejectBase64(Object value) {
        if (containsBase64(value, null)) throw new ImageApiException(422, "Base64 inputs and outputs are not supported.");
    }

    public static String request(Object client) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("client", sanitize(client, null)); value.put("upstream", null);
        return write(value, "Unable to serialize generation audit request.");
    }

    public static String withUpstream(String request, Object upstream) {
        try {
            Map<String, Object> value = JSON.readValue(request, new TypeReference<>() {});
            value.put("upstream", sanitize(upstream, null));
            return JSON.writeValueAsString(value);
        } catch (Exception e) { throw new IllegalArgumentException("Unable to serialize generation upstream request.", e); }
    }

    public static String stringify(Object value) {
        return write(sanitize(value, null), "Unable to serialize generation audit data.");
    }

    private static String write(Object value, String message) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException(message, e); }
    }

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

    private static Object sanitize(Object value, String key) {
        if (key != null && SECRET_KEYS.contains(key.toLowerCase(Locale.ROOT))) return "redacted";
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof byte[]) return "base64";
        if (value instanceof CharSequence chars) {
            String text = chars.toString();
            if (text.regionMatches(true, 0, "data:", 0, 5) && text.toLowerCase(Locale.ROOT).contains(";base64,")) return "base64";
            if (key != null && (key.toLowerCase(Locale.ROOT).contains("base64") || key.toLowerCase(Locale.ROOT).contains("b64"))) return "base64";
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
