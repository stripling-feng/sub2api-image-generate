package com.feng.system.module.image;

import com.feng.system.module.image.service.ImageModelRules;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageModelRulesTest {

    @Test
    @SuppressWarnings("unchecked")
    void mergesDefaultsAndOnlyAllowsConfiguredParameters() throws Exception {
        Class<?> type = Class.forName("com.feng.system.module.image.service.ImageModelRules");
        Method merge = type.getMethod("merge", String.class, String.class, Map.class);
        String schema = """
                [{"key":"size","type":"text"},{"key":"custom_size","type":"size"},{"key":"quality","type":"select","options":["auto","low","medium","high"]}]
                """;
        String defaults = "{\"async\":true,\"stream\":false,\"quality\":\"medium\"}";

        Map<String, Object> result = (Map<String, Object>) merge.invoke(null, schema, defaults,
                Map.of("size", "2048x2048", "custom_size", "", "quality", "high", "rogue", "ignored"));

        assertEquals("2048x2048", result.get("size"));
        assertEquals("high", result.get("quality"));
        assertEquals(true, result.get("async"));
        assertEquals(false, result.get("stream"));
        assertFalse(result.containsKey("rogue"));
        assertFalse(result.containsKey("custom_size"));
        assertTrue(result.size() >= 4);
    }
}
