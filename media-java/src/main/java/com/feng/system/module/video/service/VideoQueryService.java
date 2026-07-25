package com.feng.system.module.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.video.entity.GeneratedVideo;
import com.feng.system.module.video.entity.VideoGenerationJob;
import com.feng.system.module.video.mapper.GeneratedVideoMapper;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoQueryService {
    private final VideoGenerationJobMapper jobs;
    private final GeneratedVideoMapper videos;
    private final ObjectMapper json;
    private final VideoMaterialUploadService storage;

    public Map<String, Object> history(String profileId, int requestedPage, int requestedPageSize) {
        int page = Math.max(1, requestedPage); int pageSize = Math.min(50, Math.max(1, requestedPageSize));
        Page<VideoGenerationJob> result = jobs.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<VideoGenerationJob>().eq(VideoGenerationJob::getProfileId, profileId)
                        .orderByDesc(VideoGenerationJob::getCreatedAt));
        return Map.of("jobs", map(result.getRecords()), "page", page, "pageSize", pageSize,
                "total", result.getTotal(), "totalPages", Math.max(1, result.getPages()));
    }

    public List<Map<String, Object>> results(String profileId, String requestId) {
        return map(jobs.selectList(new LambdaQueryWrapper<VideoGenerationJob>()
                .eq(VideoGenerationJob::getProfileId, profileId).eq(VideoGenerationJob::getRequestId, requestId)
                .orderByAsc(VideoGenerationJob::getCreatedAt)));
    }

    @Transactional
    public void deleteJob(String profileId, String jobId) {
        VideoGenerationJob job = jobs.selectById(jobId);
        if (job == null || !profileId.equals(job.getProfileId())) throw new ImageApiException(404, "Video job not found.");
        if ("PENDING".equals(job.getStatus())) throw new ImageApiException(409, "Pending video jobs cannot be deleted.");
        jobs.deleteById(jobId);
        storage.deleteGenerated(jobId);
    }

    @Transactional
    public int deleteJobs(String profileId) {
        LambdaQueryWrapper<VideoGenerationJob> query = new LambdaQueryWrapper<VideoGenerationJob>()
                .eq(VideoGenerationJob::getProfileId, profileId).ne(VideoGenerationJob::getStatus, "PENDING");
        List<VideoGenerationJob> deleting = jobs.selectList(query);
        int count = jobs.delete(query);
        deleting.forEach(job -> storage.deleteGenerated(job.getId()));
        return count;
    }

    private List<Map<String, Object>> map(List<VideoGenerationJob> values) {
        if (values.isEmpty()) return List.of();
        Map<String, List<GeneratedVideo>> byJob = videos.selectList(new LambdaQueryWrapper<GeneratedVideo>()
                        .in(GeneratedVideo::getJobId, values.stream().map(VideoGenerationJob::getId).toList()))
                .stream().collect(Collectors.groupingBy(GeneratedVideo::getJobId));
        return values.stream().map(job -> job(job, byJob.getOrDefault(job.getId(), List.of()))).toList();
    }

    private Map<String, Object> job(VideoGenerationJob job, List<GeneratedVideo> results) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", job.getId()); value.put("requestId", job.getRequestId()); value.put("prompt", job.getPrompt());
        value.put("model", job.getModel()); value.put("duration", job.getDuration()); value.put("aspectRatio", job.getAspectRatio());
        value.put("resolution", job.getResolution()); value.put("generateAudio", job.getGenerateAudio() == 1);
        value.put("params", parse(job.getParams())); value.put("status", job.getStatus()); value.put("progress", job.getProgress());
        value.put("upstreamStatus", job.getUpstreamStatus()); value.put("billingStatus", job.getBillingStatus());
        value.put("billingAmount", job.getBillingAmount()); value.put("errorMessage", job.getErrorMessage());
        value.put("durationMs", job.getDurationMs()); value.put("createdAt", iso(job.getCreatedAt()));
        value.put("videos", results.stream().map(this::video).toList()); return value;
    }

    private Map<String, Object> video(GeneratedVideo video) {
        return Map.of("id", video.getId(), "jobId", video.getJobId(), "publicUrl", video.getPublicUrl(),
                "mimeType", video.getMimeType(), "createdAt", iso(video.getCreatedAt()));
    }
    private Object parse(String value) {
        try { return json.readValue(value, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception error) { return Map.of(); }
    }
    private static String iso(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC).toString(); }
}
