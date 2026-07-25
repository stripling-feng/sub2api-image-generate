package com.feng.system.module.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class VideoGatewayTest {
    private final ObjectMapper json = new ObjectMapper();
    private final VideoGateway gateway = new VideoGateway(new RestTemplateBuilder(), json, 900000);

    @Test
    void parsesSeedanceCompletedTask() throws Exception {
        Object payload = json.readValue("""
                {"id":"video_42","status":"completed","progress":100,"video_url":"https://cdn.example.com/result.mp4"}
                """, Object.class);

        VideoGateway.Task task = gateway.parseTask(payload);

        assertEquals("video_42", task.id());
        assertEquals("COMPLETED", task.status());
        assertEquals("https://cdn.example.com/result.mp4", task.resultUrl());
    }

    @Test
    void parsesSeedanceCompletedMetadataUrl() throws Exception {
        Object payload = json.readValue("""
                {"id":"video_42","status":"completed","progress":100,"metadata":{"url":"https://cdn.example.com/result.mp4"}}
                """, Object.class);

        VideoGateway.Task task = gateway.parseTask(payload);

        assertEquals("video_42", task.id());
        assertEquals("COMPLETED", task.status());
        assertEquals("https://cdn.example.com/result.mp4", task.resultUrl());
    }

    @Test
    void parsesSeedanceFailureMessage() throws Exception {
        Object payload = json.readValue("""
                {"error":{"code":"GENERATION_FAILED","message":"video generation failed"},"error_code":"GENERATION_FAILED","id":"video_42","status":"failed","video_url":null}
                """, Object.class);

        VideoGateway.Task task = gateway.parseTask(payload);

        assertEquals("video_42", task.id());
        assertEquals("FAILED", task.status());
        assertEquals("video generation failed", task.error());
    }

    @Test
    void parsesNestedGrokTask() throws Exception {
        Object payload = json.readValue("""
                {"code":"success","data":{"task_id":"task_x","status":"SUCCESS","progress":"100%","result_url":"https://cdn.example.com/grok.mp4"}}
                """, Object.class);

        VideoGateway.Task task = gateway.parseTask(payload);

        assertEquals("task_x", task.id());
        assertEquals("COMPLETED", task.status());
        assertEquals(100, task.progress());
    }

    @Test
    void parsesOmniArrayResultUrl() throws Exception {
        Object payload = json.readValue("""
                {"id":"omni_42","status":"completed","data":[{"url":"https://cdn.example.com/omni.mp4"}]}
                """, Object.class);

        assertEquals("https://cdn.example.com/omni.mp4", gateway.parseTask(payload).resultUrl());
    }

    @Test
    void exposesMultipartCreateForRepeatedInputReferences() {
        assertDoesNotThrow(() -> VideoGateway.class.getMethod("create", String.class, String.class,
                String.class, Map.class, List.class));
    }

    @Test
    void exposesAuthenticatedContentDownload() {
        assertDoesNotThrow(() -> VideoGateway.class.getMethod("download", String.class, String.class,
                String.class, String.class));
    }
}
