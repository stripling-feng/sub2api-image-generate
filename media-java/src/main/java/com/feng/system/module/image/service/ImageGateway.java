package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ImageGateway {
    private final RestTemplate http;
    private final ObjectMapper json;

    public ImageGateway(RestTemplateBuilder builder, ObjectMapper json,
                        @Value("${image.request-timeout-ms:900000}") long timeout) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(30)).setReadTimeout(Duration.ofMillis(timeout)).build();
        this.json = json;
    }

    public GatewayResponse create(String baseUrl, String apiKey, String generationPath, String editPath, Map<String, Object> body,
                                  List<Upload> images, Upload mask) {
        String endpoint = images.isEmpty() ? configuredEndpoint(baseUrl, generationPath) : configuredEndpoint(baseUrl, editPath);
        HttpHeaders headers = auth(apiKey);
        HttpEntity<?> request;
        if (images.isEmpty()) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            request = new HttpEntity<>(body, headers);
        } else {
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            body.forEach((key, value) -> { if (value != null) form.add(key, String.valueOf(value)); });
            String field = images.size() == 1 ? "image" : "image[]";
            images.forEach(image -> form.add(field, resource(image)));
            if (mask != null) form.add("mask", resource(mask));
            request = new HttpEntity<>(form, headers);
        }
        long started = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = http.exchange(endpoint, HttpMethod.POST, request, String.class);
            return new GatewayResponse(parse(response.getBody()), (int) (System.currentTimeMillis() - started));
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "sub2api request failed"), null, payload);
        }
    }

    public Task task(String baseUrl, String apiKey, String generationPath, String editPath, String operation, String taskId) {
        String endpoint = configuredEndpoint(baseUrl, "edits".equals(operation) ? editPath : generationPath)
                + "/" + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8);
        try {
            Object payload = parse(http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(auth(apiKey)), String.class).getBody());
            return parseTask(payload);
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "Image task query failed"), null, payload);
        }
    }

    public Task parseTask(Object payload) {
        Map<String, Object> map = payload instanceof Map<?, ?> raw ? cast(raw) : Map.of();
        return new Task(string(map.get("id")), lower(map.get("status")), progress(map.get("progress")),
                items(payload).stream().map(Item::url).filter(Objects::nonNull).toList(), error(payload, null), payload);
    }

    public Download content(String baseUrl, String apiKey, String generationPath, String taskId) {
        String endpoint = configuredEndpoint(baseUrl, generationPath).replaceFirst("/generations$", "/"
                + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8) + "/content");
        try {
            ResponseEntity<byte[]> response = http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(auth(apiKey)), byte[].class);
            MediaType type = response.getHeaders().getContentType();
            return new Download(response.getBody() == null ? new byte[0] : response.getBody(), type == null ? "image/png" : type.toString());
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "Image content download failed"), null, payload);
        }
    }

    public Download download(String url) {
        ResponseEntity<byte[]> response = http.getForEntity(url, byte[].class);
        MediaType type = response.getHeaders().getContentType();
        return new Download(response.getBody() == null ? new byte[0] : response.getBody(), type == null ? null : type.toString());
    }

    public List<Item> items(Object payload) {
        if (!(payload instanceof Map<?, ?> raw) || !(raw.get("data") instanceof List<?> data)) return List.of();
        List<Item> result = new ArrayList<>();
        for (Object value : data) {
            if (value instanceof Map<?, ?> item) {
                String b64 = string(item.get("b64_json"));
                String url = string(item.get("url"));
                if (b64 != null || url != null) result.add(new Item(b64, url));
            }
        }
        return result;
    }

    public static String generationsEndpoint(String baseUrl) { return legacyEndpoint(baseUrl, "generations"); }
    public static String editsEndpoint(String baseUrl) { return legacyEndpoint(baseUrl, "edits"); }
    private static String legacyEndpoint(String baseUrl, String operation) {
        String value = baseUrl.replaceAll("/+$", "");
        if (value.endsWith("/images/" + operation)) return value;
        return value.endsWith("/v1") ? value + "/images/" + operation : value + "/v1/images/" + operation;
    }
    private static String configuredEndpoint(String baseUrl, String path) {
        if (path == null || path.isBlank()) return generationsEndpoint(baseUrl);
        return baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    private HttpHeaders auth(String apiKey) { HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(apiKey); return headers; }
    private HttpEntity<ByteArrayResource> resource(Upload upload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(upload.mimeType()));
        ByteArrayResource resource = new ByteArrayResource(upload.bytes()) { @Override public String getFilename() { return upload.name(); } };
        return new HttpEntity<>(resource, headers);
    }
    private Object parse(String text) {
        if (text == null || text.isBlank()) return Map.of();
        try { return json.readValue(text, Object.class); } catch (Exception e) { return Map.of("raw", text); }
    }
    private static Map<String, Object> cast(Map<?, ?> input) { Map<String, Object> result = new LinkedHashMap<>(); input.forEach((k,v)->result.put(String.valueOf(k),v)); return result; }
    private static String string(Object value) { return value instanceof String text && !text.isBlank() ? text : null; }
    private static String lower(Object value) { String text = string(value); return text == null ? null : text.toLowerCase(); }
    private static int progress(Object value) {
        try { return Math.max(0, Math.min(100, (int) Math.round(Double.parseDouble(String.valueOf(value).replace("%", ""))))); }
        catch (Exception e) { return 0; }
    }
    private static String error(Object payload, String fallback) {
        if (payload instanceof Map<?, ?> map) {
            if (map.get("error") instanceof String text) return text;
            if (map.get("error") instanceof Map<?, ?> nested && nested.get("message") instanceof String text) return text;
            if (map.get("message") instanceof String text) return text;
        }
        return fallback;
    }

    public record Upload(String name, String mimeType, byte[] bytes) {}
    public record GatewayResponse(Object payload, int durationMs) {}
    public record Item(String b64, String url) {}
    public record Task(String id, String status, int progress, List<String> urls, String error, Object raw) {}
    public record Download(byte[] bytes, String mimeType) {}
}
