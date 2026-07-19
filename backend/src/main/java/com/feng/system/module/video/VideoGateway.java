package com.feng.system.module.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.ImageApiException;
import com.feng.system.module.image.SafeUpstreamUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class VideoGateway {
    private static final Logger log = LoggerFactory.getLogger(VideoGateway.class);
    private final RestTemplate http;
    private final ObjectMapper json;

    public VideoGateway(RestTemplateBuilder builder, ObjectMapper json,
                        @Value("${video.request-timeout-ms:900000}") long timeout) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(30)).setReadTimeout(Duration.ofMillis(timeout)).build();
        this.json = json;
    }

    public Task create(String baseUrl, String apiKey, String path, Map<String, Object> body) {
        return exchange(endpoint(baseUrl, path), HttpMethod.POST, apiKey, body);
    }

    public Task query(String baseUrl, String apiKey, String path, String taskId) {
        String url = endpoint(baseUrl, path) + "/" + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8);
        return exchange(url, HttpMethod.GET, apiKey, null);
    }

    private Task exchange(String url, HttpMethod method, String apiKey, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            if (body != null) log.info("Video upstream request {} {}", url, body);
            String text = http.exchange(url, method, new HttpEntity<>(body, headers), String.class).getBody();
            return parseTask(parse(text));
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "Video upstream request failed."), null, payload);
        }
    }

    public Task parseTask(Object payload) {
        Map<String, Object> root = map(payload);
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> nested ? map(nested) : root;
        String id = string(data.get("task_id"));
        if (id == null) id = string(data.get("id"));
        if (id == null) id = string(root.get("id"));
        String status = normalize(string(data.get("status")));
        String url = string(data.get("result_url"));
        if (url == null) url = string(data.get("video_url"));
        if (url == null && data.get("metadata") instanceof Map<?, ?> metadata) url = string(map(metadata).get("url"));
        String failure = string(data.get("fail_reason"));
        if (failure == null && data.get("error") instanceof Map<?, ?> error) failure = string(map(error).get("message"));
        if (failure == null) failure = string(data.get("error_code"));
        if (failure == null) failure = error(payload, null);
        return new Task(id, status, progress(data.get("progress")), url, failure, payload);
    }

    private Object parse(String text) {
        if (text == null || text.isBlank()) return Map.of();
        try { return json.readValue(text, Object.class); }
        catch (Exception e) { return Map.of("raw", text); }
    }

    private static String endpoint(String baseUrl, String path) {
        return SafeUpstreamUrl.requirePublicHttps(baseUrl).replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }
    private static Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> input) input.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    private static String string(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static String normalize(String status) {
        if (status == null) return "PENDING";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "success", "succeeded", "completed" -> "COMPLETED";
            case "failed", "failure", "error", "cancelled", "canceled" -> "FAILED";
            default -> "PENDING";
        };
    }
    private static int progress(Object value) {
        try { return Math.max(0, Math.min(100, (int) Math.round(Double.parseDouble(String.valueOf(value).replace("%", ""))))); }
        catch (Exception e) { return 0; }
    }
    private static String error(Object payload, String fallback) {
        if (payload instanceof Map<?, ?> map) {
            if (map.get("error") instanceof String text) return text;
            if (map.get("message") instanceof String text) return text;
        }
        return fallback;
    }

    public record Task(String id, String status, int progress, String resultUrl, String error, Object raw) {}
}
