package com.feng.system.module.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.support.SafeUpstreamUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

/**
 * 视频上游网关:封装对上游视频服务的 HTTP 调用(创建任务、查询任务、下载产物),
 * 并把结构各异的上游响应解析为统一的 Task 记录。
 */
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

    /** 以 JSON 请求体创建上游视频生成任务。 */
    public Task create(String baseUrl, String apiKey, String path, Map<String, Object> body) {
        return exchange(endpoint(baseUrl, path), HttpMethod.POST, apiKey, body, MediaType.APPLICATION_JSON);
    }

    /**
     * 以 multipart 表单创建上游任务:用于需要传多个 input_reference 参考素材的场景。
     * @param inputReferences 参考素材 URL 列表,逐个以 input_reference 字段追加
     */
    public Task create(String baseUrl, String apiKey, String path, Map<String, Object> body, List<String> inputReferences) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        body.forEach(form::add);
        inputReferences.forEach(value -> form.add("input_reference", value));
        return exchange(endpoint(baseUrl, path), HttpMethod.POST, apiKey, form, MediaType.MULTIPART_FORM_DATA);
    }

    /** 查询上游任务状态(GET {path}/{taskId})。 */
    public Task query(String baseUrl, String apiKey, String path, String taskId) {
        String url = endpoint(baseUrl, path) + "/" + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8);
        return exchange(url, HttpMethod.GET, apiKey, null, MediaType.APPLICATION_JSON);
    }

    /** 下载上游任务的生成内容(GET {path}/{taskId}/content),用于不返回结果 URL 的模型(如 omni 系列)。 */
    public ResponseEntity<byte[]> download(String baseUrl, String apiKey, String path, String taskId) {
        String url = endpoint(baseUrl, path) + "/" + UriUtils.encodePathSegment(taskId, StandardCharsets.UTF_8) + "/content";
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(apiKey);
        try {
            return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "Video content download failed."), null, payload);
        }
    }

    private Task exchange(String url, HttpMethod method, String apiKey, Object body, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(contentType);
        try {
            if (body != null) log.info("Video upstream request {} {}", url, body);
            String text = http.exchange(url, method, new HttpEntity<>(body, headers), String.class).getBody();
            return parseTask(parse(text));
        } catch (HttpStatusCodeException e) {
            Object payload = parse(e.getResponseBodyAsString());
            throw new ImageApiException(e.getStatusCode().value(), error(payload, "Video upstream request failed."), null, payload);
        }
    }

    /**
     * 把上游响应解析为统一 Task:兼容多种字段命名——
     * 任务 ID 依次取 task_id/id/根节点 id,结果 URL 依次取 result_url/video_url/metadata.url/data[0].url,
     * 失败原因依次取 fail_reason/error.message/error_code/顶层 error 或 message。
     */
    public Task parseTask(Object payload) {
        Map<String, Object> root = map(payload);
        // 部分上游把实际数据包在 data 对象里,否则直接用根节点
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> nested ? map(nested) : root;
        String id = string(data.get("task_id"));
        if (id == null) id = string(data.get("id"));
        if (id == null) id = string(root.get("id"));
        String status = normalize(string(data.get("status")));
        String url = string(data.get("result_url"));
        if (url == null) url = string(data.get("video_url"));
        if (url == null && data.get("metadata") instanceof Map<?, ?> metadata) url = string(map(metadata).get("url"));
        if (url == null && root.get("data") instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> item) url = string(map(item).get("url"));
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
    // 归一化上游状态:成功类 -> COMPLETED,失败/取消类 -> FAILED,其余(含空)一律视为 PENDING
    private static String normalize(String status) {
        if (status == null) return "PENDING";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "success", "succeeded", "completed" -> "COMPLETED";
            case "failed", "failure", "error", "cancelled", "canceled" -> "FAILED";
            default -> "PENDING";
        };
    }
    // 解析进度:兼容 "50%"、数字、字符串等形式,并限制在 0~100,解析失败按 0 处理
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

    /** 统一的上游任务视图:任务 ID、归一化状态、进度、结果 URL、失败原因及原始响应。 */
    public record Task(String id, String status, int progress, String resultUrl, String error, Object raw) {}
}
