package com.feng.system.module.video.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 视频任务查询服务:提供按 API Key 隔离的历史分页、按 requestId 查询结果,
 * 以及删除任务(连同产物记录与本地文件)的能力。
 */
@Service
@RequiredArgsConstructor
public class VideoQueryService {
    private final MediaTaskMapper tasks;
    private final MediaTaskResultService resultService;
    private final ObjectMapper json;
    @Value("${image.upload-dir:./uploads}") private String uploadDir;

    /** 分页查询视频任务历史,按创建时间倒序;页码最小 1,每页 1~50 条。 */
    public Map<String, Object> history(String apiKey, int requestedPage, int requestedPageSize) {
        int page = Math.max(1, requestedPage);
        int pageSize = Math.min(50, Math.max(1, requestedPageSize));
        Page<MediaTask> result = tasks.selectPage(new Page<>(page, pageSize), new QueryWrapper<MediaTask>()
                .eq("api_key", apiKey)
                .eq("task_type", "VIDEO")
                .orderByAsc("CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END")
                .orderByDesc("created_at"));
        return Map.of("jobs", map(result.getRecords()), "page", page, "pageSize", pageSize,
                "total", result.getTotal(), "totalPages", Math.max(1, result.getPages()));
    }

    /** 按 requestId 查询同一批生成请求下的全部任务及结果,按创建时间升序。 */
    public List<Map<String, Object>> results(String apiKey, String requestId) {
        return map(tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getApiKey, apiKey).eq(MediaTask::getTaskType, "VIDEO")
                .eq(MediaTask::getRequestId, requestId).orderByAsc(MediaTask::getCreatedAt)));
    }

    /** 删除单个视频任务:PENDING 状态禁止删除,同时清理产物记录与本地视频文件。 */
    @Transactional
    public void deleteJob(String apiKey, String taskId) {
        MediaTask task = tasks.selectOne(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getId, taskId).eq(MediaTask::getApiKey, apiKey).eq(MediaTask::getTaskType, "VIDEO"));
        if (task == null) throw new ImageApiException(404, "Video job not found.");
        if ("PENDING".equals(task.getStatus())) throw new ImageApiException(409, "Pending video jobs cannot be deleted.");
        List<MediaTaskResult> values = resultService.list(List.of(taskId));
        values.forEach(this::deleteFile);
        resultService.deleteByTaskIds(List.of(taskId));
        tasks.deleteById(taskId);
    }

    /** 批量删除当前 API Key 下所有非 PENDING 的视频任务及产物,返回删除的任务数。 */
    @Transactional
    public int deleteJobs(String apiKey) {
        List<MediaTask> deleting = tasks.selectList(new LambdaQueryWrapper<MediaTask>()
                .eq(MediaTask::getApiKey, apiKey).eq(MediaTask::getTaskType, "VIDEO")
                .ne(MediaTask::getStatus, "PENDING"));
        List<String> ids = deleting.stream().map(MediaTask::getId).toList();
        resultService.list(ids).forEach(this::deleteFile);
        resultService.deleteByTaskIds(ids);
        return ids.isEmpty() ? 0 : tasks.deleteBatchIds(ids);
    }

    private List<Map<String, Object>> map(List<MediaTask> values) {
        if (values.isEmpty()) return List.of();
        Map<String, List<MediaTaskResult>> byTask = resultService.list(values.stream().map(MediaTask::getId).toList())
                .stream().collect(Collectors.groupingBy(MediaTaskResult::getTaskId));
        return values.stream().map(task -> job(task, byTask.getOrDefault(task.getId(), List.of()))).toList();
    }

    private Map<String, Object> job(MediaTask task, List<MediaTaskResult> results) {
        Map<String, Object> data = MediaTaskData.read(json, task.getTaskData());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", task.getId()); value.put("requestId", task.getRequestId()); value.put("prompt", data.get("prompt"));
        value.put("model", data.get("model")); value.put("duration", data.get("duration"));
        value.put("aspectRatio", data.get("aspectRatio")); value.put("resolution", data.get("resolution"));
        value.put("generateAudio", MediaTaskData.bool(data.get("generateAudio"), false));
        value.put("params", data.getOrDefault("params", Map.of())); value.put("status", task.getStatus());
        value.put("progress", task.getProgress()); value.put("upstreamStatus", data.get("upstreamStatus"));
        value.put("billingStatus", task.getBillingStatus()); value.put("billingAmount", task.getBillingAmount());
        value.put("errorMessage", task.getErrorMessage()); value.put("durationMs", task.getDurationMs());
        value.put("createdAt", iso(task.getCreatedAt())); value.put("videos", results.stream().map(this::video).toList());
        return value;
    }

    private Map<String, Object> video(MediaTaskResult result) {
        Map<String, Object> metadata = resultService.metadata(result);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", result.getId()); value.put("jobId", result.getTaskId()); value.put("publicUrl", result.getAddress());
        value.put("mimeType", metadata.get("mimeType")); value.put("createdAt", iso(result.getCreatedAt()));
        return value;
    }

    // 删除产物对应的本地文件:仅允许删除上传根目录内的路径,防止路径穿越
    private void deleteFile(MediaTaskResult result) {
        Object value = resultService.metadata(result).get("filePath");
        if (value == null) return;
        try {
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            Path target = Path.of(String.valueOf(value)).toAbsolutePath().normalize();
            if (target.startsWith(root)) Files.deleteIfExists(target);
        } catch (Exception error) {
            throw new ImageApiException(500, "Generated video deletion failed.");
        }
    }

    private static String iso(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }
}
