package com.feng.system.module.image.service;

import com.feng.system.module.image.exception.ImageApiException;
import com.feng.system.module.image.support.ImageTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.module.image.entity.GeneratedImage;
import com.feng.system.module.image.entity.GenerationJob;
import com.feng.system.module.image.entity.PromptTemplate;
import com.feng.system.module.image.mapper.GeneratedImageMapper;
import com.feng.system.module.image.mapper.GenerationJobMapper;
import com.feng.system.module.image.mapper.PromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageQueryService {
    private final GenerationJobMapper jobMapper;
    private final GeneratedImageMapper imageMapper;
    private final PromptTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;
    @Value("${image.upload-dir:./uploads}") private String uploadDir;

    public Map<String, Object> history(String profileId, int requestedPage, int requestedPageSize) {
        int page = Math.max(1, requestedPage);
        int pageSize = Math.min(50, Math.max(1, requestedPageSize));
        Page<GenerationJob> result = jobMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<GenerationJob>().eq(GenerationJob::getProfileId, profileId)
                        .orderByDesc(GenerationJob::getCreatedAt));
        return Map.of("jobs", jobs(result.getRecords()), "page", page, "pageSize", pageSize,
                "total", result.getTotal(), "totalPages", Math.max(1, result.getPages()));
    }

    public List<Map<String, Object>> results(String profileId, String requestId) {
        List<GenerationJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<GenerationJob>()
                .eq(GenerationJob::getProfileId, profileId)
                .apply("params ->> 'request_id' = {0}", requestId)
                .orderByAsc(GenerationJob::getCreatedAt));
        return jobs(jobs);
    }

    @Transactional
    public void deleteImage(String profileId, String imageId) {
        GeneratedImage image = imageMapper.selectById(imageId);
        GenerationJob job = image == null ? null : jobMapper.selectById(image.getJobId());
        if (image == null || job == null || !profileId.equals(job.getProfileId())) throw new ImageApiException(404, "Image not found.");
        deleteFile(image.getFilePath());
        imageMapper.deleteById(imageId);
    }

    @Transactional
    public int deleteJobs(String profileId) {
        List<GenerationJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<GenerationJob>().eq(GenerationJob::getProfileId, profileId));
        for (GeneratedImage image : images(jobs)) deleteFile(image.getFilePath());
        return jobMapper.delete(new LambdaQueryWrapper<GenerationJob>().eq(GenerationJob::getProfileId, profileId));
    }

    @Transactional
    public void deleteJob(String profileId, String jobId) {
        GenerationJob job = jobMapper.selectById(jobId);
        if (job == null || !profileId.equals(job.getProfileId())) throw new ImageApiException(404, "Job not found.");
        for (GeneratedImage image : images(List.of(job))) deleteFile(image.getFilePath());
        jobMapper.deleteById(jobId);
    }

    public List<Map<String, Object>> templates(String profileId) {
        return templateMapper.selectList(new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getProfileId, profileId)
                        .orderByDesc(PromptTemplate::getUpdatedAt)).stream().map(this::template).toList();
    }

    public Map<String, Object> createTemplate(String profileId, String title, String prompt, Object params) {
        if (title == null || title.isBlank() || title.length() > 80 || prompt == null || prompt.isBlank())
            throw new ImageApiException(422, "Invalid request.");
        PromptTemplate template = new PromptTemplate();
        template.setId(id()); template.setProfileId(profileId); template.setTitle(title); template.setPrompt(prompt);
        template.setParams(json(params == null ? Map.of() : params));
        template.setCreatedAt(ImageTime.now()); template.setUpdatedAt(ImageTime.now());
        templateMapper.insert(template);
        return template(template);
    }

    public void deleteTemplate(String profileId, String id) {
        templateMapper.delete(new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getId, id)
                .eq(PromptTemplate::getProfileId, profileId));
    }

    private List<Map<String, Object>> jobs(List<GenerationJob> jobs) {
        Map<String, List<GeneratedImage>> byJob = images(jobs).stream().collect(Collectors.groupingBy(GeneratedImage::getJobId));
        return jobs.stream().map(job -> job(job, byJob.getOrDefault(job.getId(), List.of()))).toList();
    }

    private List<GeneratedImage> images(List<GenerationJob> jobs) {
        if (jobs.isEmpty()) return List.of();
        return imageMapper.selectList(new LambdaQueryWrapper<GeneratedImage>().in(GeneratedImage::getJobId,
                jobs.stream().map(GenerationJob::getId).toList()).orderByAsc(GeneratedImage::getSourceIndex));
    }

    private Map<String, Object> job(GenerationJob job, List<GeneratedImage> images) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", job.getId()); result.put("prompt", job.getPrompt()); result.put("negativePrompt", job.getNegativePrompt());
        result.put("model", job.getModel()); result.put("size", job.getSize()); result.put("quality", job.getQuality());
        result.put("style", job.getStyle()); result.put("count", job.getCount()); result.put("responseFormat", job.getResponseFormat());
        result.put("params", parse(job.getParams())); result.put("status", job.getStatus()); result.put("progress", job.getProgress());
        result.put("upstreamStatus", job.getUpstreamStatus()); result.put("billingStatus", job.getBillingStatus());
        result.put("billingAmount", job.getBillingAmount()); result.put("errorMessage", job.getErrorMessage());
        result.put("durationMs", job.getDurationMs()); result.put("createdAt", iso(job.getCreatedAt()));
        result.put("images", images.stream().map(this::image).toList());
        return result;
    }

    private Map<String, Object> image(GeneratedImage image) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", image.getId()); result.put("jobId", image.getJobId()); result.put("publicUrl", image.getPublicUrl());
        result.put("mimeType", image.getMimeType()); result.put("width", image.getWidth()); result.put("height", image.getHeight());
        result.put("sizeBytes", image.getSizeBytes()); result.put("createdAt", iso(image.getCreatedAt()));
        return result;
    }

    private Map<String, Object> template(PromptTemplate template) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId()); result.put("title", template.getTitle()); result.put("prompt", template.getPrompt());
        result.put("params", parse(template.getParams())); result.put("createdAt", iso(template.getCreatedAt()));
        result.put("updatedAt", iso(template.getUpdatedAt())); return result;
    }

    private Object parse(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new ImageApiException(422, "Invalid request."); }
    }

    private void deleteFile(String file) {
        try {
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            Path target = Path.of(file).toAbsolutePath().normalize();
            if (target.startsWith(root)) Files.deleteIfExists(target);
        } catch (Exception ignored) { }
    }

    private static String iso(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC).toString(); }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
}
