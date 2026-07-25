package com.feng.system.module.image;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.support.GenerationAuditJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationAuditJsonTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void keepsClientAndUpstreamRequestViews() throws Exception {
        String request = GenerationAuditJson.request(Map.of("model", "video-model"));
        request = GenerationAuditJson.withUpstream(request, Map.of("image_url", "https://media.example.com/ref.png"));

        Map<?, ?> stored = json.readValue(request, Map.class);
        assertEquals("video-model", ((Map<?, ?>) stored.get("client")).get("model"));
        assertEquals("https://media.example.com/ref.png", ((Map<?, ?>) stored.get("upstream")).get("image_url"));
    }

    @Test
    void rejectsBase64InputsAndBase64ResponseFormat() {
        assertThrows(ImageApiException.class, () -> GenerationAuditJson.rejectBase64(
                Map.of("nested", List.of(Map.of("image", "data:image/png;base64,aGVsbG8=")))));
        assertThrows(ImageApiException.class, () -> GenerationAuditJson.rejectBase64(
                Map.of("referenceImages", List.of(Map.of("data", "a".repeat(256))))));
        assertThrows(ImageApiException.class, () -> GenerationAuditJson.rejectBase64(
                Map.of("referenceImages", List.of("aGVsbG8="))));
        assertThrows(ImageApiException.class, () -> GenerationAuditJson.rejectBase64(
                Map.of("responseFormat", "b64_json")));
    }

    @Test
    void recursivelyReplacesBase64AndBinaryValues() throws Exception {
        String encoded = "a".repeat(256);
        String value = GenerationAuditJson.stringify(Map.of(
                "image", "data:image/png;base64," + encoded,
                "nested", List.of(Map.of("b64_json", encoded)),
                "bytes", new byte[] {1, 2, 3},
                "prompt", "draw a city"));

        Map<?, ?> stored = json.readValue(value, Map.class);
        assertEquals("base64", stored.get("image"));
        assertEquals("base64", ((Map<?, ?>) ((List<?>) stored.get("nested")).get(0)).get("b64_json"));
        assertEquals("base64", stored.get("bytes"));
        assertEquals("draw a city", stored.get("prompt"));
    }

    @Test
    void appendsSanitizedResponsesInOrder() throws Exception {
        String first = GenerationAuditJson.append(null, "create", Map.of("status", "queued"));
        String second = GenerationAuditJson.append(first, "poll", Map.of("data", "data:image/png;base64,abc"));

        List<?> responses = json.readValue(second, List.class);
        assertEquals(2, responses.size());
        assertEquals("create", ((Map<?, ?>) responses.get(0)).get("phase"));
        assertEquals("base64", ((Map<?, ?>) ((Map<?, ?>) responses.get(1)).get("payload")).get("data"));
    }
}
