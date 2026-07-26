package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 图片生成上游网关：封装对 sub2api 上游服务的图片生成 HTTP 调用，
 * 包括提交生成请求、查询异步任务状态、下载图片内容，并统一解析响应与错误信息。
 */
@Service
public class ImageGateway {
    private final RestTemplate http;
    private final ObjectMapper json;

    public ImageGateway(RestTemplateBuilder builder, ObjectMapper json,
                        @Value("${image.request-timeout-ms:900000}") long timeout) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(30)).setReadTimeout(Duration.ofMillis(timeout)).build();
        this.json = json;
    }

    /**
     * 向上游提交图片生成请求。
     * 上游统一以 JSON 提交，参考图通过 body.images 的 URL 数组传递。
     *
     * @param generationPath 生成接口路径（相对 baseUrl）
     * @return 上游响应负载及本次请求耗时
     */
    public GatewayResponse create(String baseUrl, String apiKey, String generationPath, Map<String, Object> body) {
        String endpoint = configuredEndpoint(baseUrl, generationPath);
        HttpHeaders headers = auth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> request = new HttpEntity<>(body, headers);
        long started = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = http.exchange(endpoint, HttpMethod.POST, request, String.class);
            return new GatewayResponse(parse(response.getBody()), (int) (System.currentTimeMillis() - started));
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "sub2api request failed"), null, payload);
        }
    }

    /**
     * 查询上游异步任务的当前状态。
     *
     * @param taskId    上游任务 ID
     */
    public Task task(String baseUrl, String apiKey, String generationPath, String taskId) {
        String endpoint = configuredEndpoint(baseUrl, generationPath) + "/" + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8);
        try {
            Object payload = parse(http.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(auth(apiKey)), String.class).getBody());
            return parseTask(payload);
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "Image task query failed"), null, payload);
        }
    }

    /**
     * 将上游返回的原始负载解析为统一的任务对象（ID、状态、进度、结果 URL、错误信息）。
     */
    public Task parseTask(Object payload) {
        Map<String, Object> map = payload instanceof Map<?, ?> raw ? cast(raw) : Map.of();
        return new Task(string(map.get("id")), lower(map.get("status")), progress(map.get("progress")),
                items(payload).stream().map(Item::url).filter(Objects::nonNull).toList(), error(payload, null), payload);
    }

    /**
     * 下载指定任务生成的图片内容（二进制），Content-Type 缺失时默认按 image/png 处理。
     */
    public Download content(String baseUrl, String apiKey, String generationPath, String taskId) {
        // 把 .../generations 结尾替换为 .../{taskId}/content 得到内容下载地址
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

    /**
     * 直接按 URL 下载图片字节流（用于上游返回图片 URL 的场景）。
     */
    public Download download(String url) {
        ResponseEntity<byte[]> response = http.getForEntity(url, byte[].class);
        MediaType type = response.getHeaders().getContentType();
        return new Download(response.getBody() == null ? new byte[0] : response.getBody(), type == null ? null : type.toString());
    }

    /**
     * 从上游响应的 data 数组中提取图片结果项（b64_json 或 url），无有效结果时返回空列表。
     */
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
    private Object parse(String text) {
        if (text == null || text.isBlank()) return Map.of();
        // 非 JSON 响应不抛异常，包装为 {"raw": 原文} 以便留存审计
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

    /** 上传文件（参考图或蒙版）：文件名、MIME 类型与字节内容。 */
    public record Upload(String name, String mimeType, byte[] bytes) {}
    /** 上游创建请求的响应：解析后的负载与请求耗时（毫秒）。 */
    public record GatewayResponse(Object payload, int durationMs) {}
    /** 单条图片结果：base64 内容或图片 URL（二者至少其一非空）。 */
    public record Item(String b64, String url) {}
    /** 上游异步任务的统一视图：ID、状态、进度、结果 URL 列表、错误信息及原始负载。 */
    public record Task(String id, String status, int progress, List<String> urls, String error, Object raw) {}
    /** 下载结果：图片字节与 MIME 类型。 */
    public record Download(byte[] bytes, String mimeType) {}
}
