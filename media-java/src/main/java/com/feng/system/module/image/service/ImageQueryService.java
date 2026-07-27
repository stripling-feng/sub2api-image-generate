package com.feng.system.module.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.media.entity.MediaTask;
import com.feng.system.module.media.entity.MediaTaskResult;
import com.feng.system.module.media.mapper.MediaTaskMapper;
import com.feng.system.module.media.service.MediaTaskData;
import com.feng.system.module.media.service.MediaTaskResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageQueryService {
    private final MediaTaskMapper tasks;
    private final MediaTaskResultService resultService;
    private final ImageStorageService storage;
    private final ObjectMapper json;

    public Map<String, Object> history(String apiKey, int requestedPage, int requestedPageSize) {
        int page = Math.max(1, requestedPage);
        int pageSize = Math.min(50, Math.max(1, requestedPageSize));
        Page<MediaTask> result = tasks.selectPage(new Page<>(page, pageSize), new QueryWrapper<MediaTask>()
                .eq("api_key", apiKey)
                .eq("task_type", "IMAGE")
                .orderByAsc("CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END")
                .orderByDesc("created_at"));
        return Map.of("jobs", mapTasks(result.getRecords()), "page", page, "pageSize", pageSize,
                "total", result.getTotal(), "totalPages", Math.max(1, result.getPages()));
    }

    public List<Map<String, Object>> results(String apiKey, String requestId) {
        List<MediaTask> values = tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getApiKey, apiKey).eq(MediaTask::getTaskType, "IMAGE")
                .eq(MediaTask::getRequestId, requestId).orderByAsc(MediaTask::getCreatedAt));
        return mapTasks(values);
    }

    public List<Map<String, Object>> compactResults(String apiKey, String requestId) {
        return results(apiKey, requestId).stream().map(this::compactTask).toList();
    }

    public DownloadedImage download(String apiKey, String imageId) {
        MediaTaskResult result = resultService.findOwnedImageResult(apiKey, imageId);
        if (result == null) throw new ImageApiException(404, "Image not found.");
        ImageGateway.Download download = storage.download(result);
        Map<String, Object> metadata = resultService.metadata(result);
        String mimeType = download.mimeType() != null && !download.mimeType().isBlank()
                ? download.mimeType() : String.valueOf(metadata.getOrDefault("mimeType", "image/png"));
        return new DownloadedImage(download.bytes(), mimeType, filename(result, mimeType));
    }

    @Transactional
    public int deleteJobs(String apiKey) {
        List<MediaTask> values = tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getApiKey, apiKey).eq(MediaTask::getTaskType, "IMAGE"));
        List<String> ids = values.stream().map(MediaTask::getId).toList();
        for (MediaTaskResult result : results(ids)) storage.delete(result);
        resultService.deleteByTaskIds(ids);
        return ids.isEmpty() ? 0 : tasks.deleteBatchIds(ids);
    }

    @Transactional
    public void deleteJob(String apiKey, String taskId) {
        MediaTask task = tasks.selectOne(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getId, taskId).eq(MediaTask::getApiKey, apiKey).eq(MediaTask::getTaskType, "IMAGE"));
        if (task == null) throw new ImageApiException(404, "Job not found.");
        List<MediaTaskResult> values = results(List.of(taskId));
        values.forEach(storage::delete);
        resultService.deleteByTaskIds(List.of(taskId));
        tasks.deleteById(taskId);
    }

    private List<Map<String, Object>> mapTasks(List<MediaTask> values) {
        if (values.isEmpty()) return List.of();
        List<String> ids = values.stream().map(MediaTask::getId).toList();
        Map<String, List<MediaTaskResult>> byTask = results(ids).stream()
                .collect(Collectors.groupingBy(MediaTaskResult::getTaskId));
        return values.stream().map(task -> mapTask(task, byTask.getOrDefault(task.getId(), List.of()))).toList();
    }

    private List<MediaTaskResult> results(List<String> ids) {
        return resultService.list(ids);
    }

    private Map<String, Object> mapTask(MediaTask task, List<MediaTaskResult> values) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("prompt", data.get("prompt"));
        result.put("negativePrompt", data.get("negativePrompt"));
        result.put("model", data.get("model"));
        result.put("size", data.get("size"));
        result.put("quality", data.get("quality"));
        result.put("style", data.get("style"));
        result.put("count", data.getOrDefault("count", 1));
        result.put("responseFormat", data.get("responseFormat"));
        result.put("params", data.getOrDefault("params", Map.of()));
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        result.put("upstreamStatus", data.get("upstreamStatus"));
        result.put("billingStatus", task.getBillingStatus());
        result.put("billingAmount", task.getBillingAmount());
        result.put("errorMessage", task.getErrorMessage());
        result.put("durationMs", task.getDurationMs());
        result.put("createdAt", iso(task.getCreatedAt()));
        result.put("images", values.stream().map(this::mapResult).toList());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compactTask(Map<String, Object> task) {
        Map<String, Object> params = task.get("params") instanceof Map<?, ?> value
                ? (Map<String, Object>) value : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request_id", params.get("request_id"));
        result.put("request_index", params.get("request_index"));
        result.put("request_total", params.get("request_total"));
        result.put("status", task.get("status"));
        result.put("progress", task.get("progress"));
        result.put("errorMessage", task.get("errorMessage"));
        Object url = null;
        if (task.get("images") instanceof List<?> images && !images.isEmpty()
                && images.get(0) instanceof Map<?, ?> image) {
            url = image.get("publicUrl");
        }
        result.put("url", url);
        return result;
    }

    private Map<String, Object> mapResult(MediaTaskResult value) {
        Map<String, Object> metadata = resultService.metadata(value);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId());
        result.put("jobId", value.getTaskId());
        result.put("publicUrl", value.getAddress());
        result.put("mimeType", metadata.get("mimeType"));
        result.put("width", number(metadata.get("width")));
        result.put("height", number(metadata.get("height")));
        result.put("sizeBytes", number(metadata.get("sizeBytes")));
        result.put("createdAt", iso(value.getCreatedAt()));
        return result;
    }

    private Integer number(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (Exception ignored) { return null; }
    }

    private static String filename(MediaTaskResult result, String mimeType) {
        String address = result.getAddress() == null ? "" : result.getAddress().split("\\?")[0];
        int slash = address.lastIndexOf('/');
        String name = slash >= 0 ? address.substring(slash + 1) : address;
        if (!name.isBlank()) return name;
        return result.getId() + extension(mimeType);
    }

    private static String extension(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".png";
        };
    }

    private static String iso(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    public record DownloadedImage(byte[] bytes, String mimeType, String filename) {}
}
