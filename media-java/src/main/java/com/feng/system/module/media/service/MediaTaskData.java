package com.feng.system.module.media.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MediaTaskData {
    private MediaTaskData() {}

    public static Map<String, Object> read(ObjectMapper json, String value) {
        if (value == null || value.isBlank()) return new LinkedHashMap<>();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return new LinkedHashMap<>(); }
    }

    public static String write(ObjectMapper json, Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Unable to serialize media task data", e); }
    }

    public static Object get(String value, String key) {
        return read(new ObjectMapper(), value).get(key);
    }

    public static void put(Map<String, Object> data, String key, Object value) {
        if (value == null) data.remove(key);
        else data.put(key, value);
    }

    public static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }

    public static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
