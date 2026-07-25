package com.feng.system.module.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.image.*;
import com.feng.system.module.video.entity.GeneratedVideo;
import com.feng.system.module.video.entity.VideoGenerationJob;
import com.feng.system.module.video.mapper.GeneratedVideoMapper;
import com.feng.system.module.video.mapper.VideoGenerationJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Service
@RequiredArgsConstructor
public class VideoTaskPoller {
    private final VideoGenerationJobMapper jobs;
    private final GeneratedVideoMapper videos;
    private final VideoGateway gateway;
    private final Sub2apiBillingService billing;
    private final ImageModelConfigService modelConfigs;
    private final VideoMaterialUploadService storage;
    @Value("${video.poll-max-duration-ms:7200000}") private long maxDuration = 7_200_000;
    @Value("${video.poll-lease-ms:60000}") private long leaseMillis = 60_000;
    @Value("${video.poll-interval-ms:5000}") private long pollInterval = 5_000;

    @Scheduled(fixedDelayString = "${video.poll-interval-ms:5000}", initialDelayString = "${video.poll-interval-ms:5000}")
    public void poll() {
        LocalDateTime now = ImageTime.now();
        List<VideoGenerationJob> due = jobs.selectList(new LambdaQueryWrapper<VideoGenerationJob>()
                .eq(VideoGenerationJob::getStatus, "PENDING").isNotNull(VideoGenerationJob::getUpstreamTaskId)
                .and(q -> q.isNull(VideoGenerationJob::getNextPollAt).or().le(VideoGenerationJob::getNextPollAt, now))
                .and(q -> q.isNull(VideoGenerationJob::getPollLeaseUntil).or().lt(VideoGenerationJob::getPollLeaseUntil, now))
                .orderByAsc(VideoGenerationJob::getNextPollAt).last("LIMIT 10"));
        for (VideoGenerationJob job : due) if (claim(job.getId(), now)) process(job);
    }

    private boolean claim(String id, LocalDateTime now) {
        VideoGenerationJob update = new VideoGenerationJob();
        update.setId(id); update.setPollLeaseUntil(now.plusNanos(leaseMillis * 1_000_000)); update.setUpdatedAt(now);
        return jobs.update(update, new LambdaQueryWrapper<VideoGenerationJob>().eq(VideoGenerationJob::getId, id)
                .eq(VideoGenerationJob::getStatus, "PENDING")
                .and(q -> q.isNull(VideoGenerationJob::getPollLeaseUntil).or().lt(VideoGenerationJob::getPollLeaseUntil, now))) == 1;
    }

    void process(VideoGenerationJob job) {
        try {
            ImageModelConfigService.RuntimeModel runtime = modelConfigs.requireVideo(job.getModelConfigId());
            VideoGateway.Task task = gateway.query(runtime.provider().getBaseUrl(), runtime.provider().getVideoApiKey(),
                    runtime.model().getGenerationPath(), job.getUpstreamTaskId());
            job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "poll", task.raw()));
            long elapsed = Duration.between(job.getCreatedAt(), ImageTime.now()).toMillis();
            if (elapsed >= maxDuration) fail(job, "Video task timed out.", elapsed);
            else if ("FAILED".equals(task.status())) fail(job, task.error() == null ? "Video task failed." : task.error(), elapsed);
            else if ("COMPLETED".equals(task.status()) && !isOmni(runtime)
                    && (task.resultUrl() == null || task.resultUrl().isBlank()))
                fail(job, "Completed video task returned no result URL.", elapsed);
            else if ("COMPLETED".equals(task.status())) complete(job, task, runtime, elapsed);
            else pending(job, task);
        } catch (Exception error) { retry(job, error); }
    }

    private void complete(VideoGenerationJob job, VideoGateway.Task task, ImageModelConfigService.RuntimeModel runtime, long elapsed) {
        GeneratedVideo video = videos.selectOne(new LambdaQueryWrapper<GeneratedVideo>().eq(GeneratedVideo::getJobId, job.getId()));
        if (video == null) {
            String publicUrl;
            String mime = "video/mp4";
            if (isOmni(runtime)) {
                ResponseEntity<byte[]> content = gateway.download(runtime.provider().getBaseUrl(),
                        runtime.provider().getVideoApiKey(), runtime.model().getGenerationPath(), job.getUpstreamTaskId());
                mime = content.getHeaders().getContentType() == null ? null : content.getHeaders().getContentType().toString();
                java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("status", content.getStatusCode().value()); response.put("contentType", mime);
                response.put("body", content.getBody());
                job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "content", response));
                VideoMaterialUploadService.Uploaded stored = storage.storeGenerated(job.getId(), content.getBody(), mime);
                publicUrl = stored.url(); mime = stored.mimeType();
            } else publicUrl = SafeUpstreamUrl.requirePublicHttps(task.resultUrl());
            video = new GeneratedVideo();
            video.setId(UUID.randomUUID().toString().replace("-", "")); video.setJobId(job.getId());
            video.setPublicUrl(publicUrl); video.setMimeType(mime); video.setCreatedAt(ImageTime.now()); videos.insert(video);
        }
        if ("RESERVED".equals(job.getBillingStatus())) {
            String usage = billing.settleVideo(job.getId(), job.getBillingApiKeyId(), job.getBillingUserId(), job.getBillingAccountId(),
                    job.getBillingAmount(), runtime.model().getModelKey(), runtime.model().getUpstreamModel(),
                    runtime.model().getGenerationPath(), job.getDuration());
            job.setBillingStatus("CHARGED"); job.setBillingUsageLogId(usage); job.setBillingSettledAt(ImageTime.now());
        }
        job.setStatus("SUCCEEDED"); job.setProgress(100); job.setUpstreamStatus("completed");
        job.setDurationMs((int) elapsed); job.setCompletedAt(ImageTime.now()); job.setNextPollAt(null);
        job.setPollLeaseUntil(null); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private void fail(VideoGenerationJob job, String message, long elapsed) {
        if ("RESERVED".equals(job.getBillingStatus())) {
            try {
                billing.releaseVideo(job.getId(), job.getBillingApiKeyId(), job.getBillingUserId(), job.getBillingAmount());
                job.setBillingStatus("RELEASED");
            } catch (Exception error) { job.setBillingStatus("RELEASE_FAILED"); job.setBillingError(message(error)); }
        }
        job.setStatus("FAILED"); job.setUpstreamStatus("failed"); job.setErrorMessage(message);
        job.setDurationMs((int) elapsed); job.setCompletedAt(ImageTime.now()); job.setNextPollAt(null);
        job.setPollLeaseUntil(null); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private void pending(VideoGenerationJob job, VideoGateway.Task task) {
        job.setProgress(task.progress()); job.setUpstreamStatus(task.status().toLowerCase()); job.setPollErrorCount(0);
        job.setPollLeaseUntil(null); job.setNextPollAt(ImageTime.now().plusNanos(pollInterval * 1_000_000));
        job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private void retry(VideoGenerationJob job, Exception error) {
        int attempts = (job.getPollErrorCount() == null ? 0 : job.getPollErrorCount()) + 1;
        long backoff = Math.min(30_000, pollInterval * (1L << Math.min(4, attempts - 1)));
        job.setRawResponses(GenerationAuditJson.append(job.getRawResponses(), "poll_error", payload(error)));
        job.setPollErrorCount(attempts); job.setBillingError(message(error)); job.setPollLeaseUntil(null);
        job.setNextPollAt(ImageTime.now().plusNanos(backoff * 1_000_000)); job.setUpdatedAt(ImageTime.now()); jobs.updateById(job);
    }

    private static String message(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static Object payload(Exception error) {
        return error instanceof ImageApiException api && api.getPayload() != null
                ? api.getPayload() : java.util.Map.of("message", message(error));
    }
    private static boolean isOmni(ImageModelConfigService.RuntimeModel runtime) {
        return runtime.model().getModelKey() != null && runtime.model().getModelKey().startsWith("omni-");
    }
}
